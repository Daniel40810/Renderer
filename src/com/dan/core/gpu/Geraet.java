package com.dan.core.gpu;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaBudget;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Das Gerät (Werkbuch HalligHooge Teil A, Abschnitt 6): Instanz, Grafikkarte, Warteschlangen, Fähigkeiten und
 * Speicher über VMA. Vulkan 1.3 über LWJGL 3; alles Weitere in {@code core.gpu} hängt an einem Gerät.
 * <ul>
 * <li><b>Warteschlangen:</b> {@link #grafik} (Grafik und Rechnen), {@link #rechnen} (eine eigene Rechen-Familie neben
 * dem Bild, wenn das Gerät sie hat – sonst dieselbe wie {@code grafik}) und {@link #uebertragung} (ebenso). Die Physik
 * rechnet auf {@code rechnen}, das Bild auf {@code grafik}; dazwischen stehen {@link Zeitleiste}n.</li>
 * <li><b>Fähigkeiten:</b> Eingeschaltet wird jedes Merkmal, das das Gerät kann, außer den Robustheits-Merkmalen (sie
 * kosten Zeit). {@link #kann} sagt, was da ist; ein Modul fragt, bevor es Strahlabfrage, float16 oder Untergruppen
 * nutzt.</li>
 * <li><b>Speicher:</b> {@link #heaps()} zeigt die Heaps mit Budget (auf der 680M der geteilte Hauptspeicher), und
 * jedes Modul bucht auf ein {@link Konto} mit eigener Grenze. Eine Zuteilung über {@link #groessteZuteilung} (auf der
 * 680M 2 GB) wird abgelehnt: Solche Felder führt man in Stücken.</li>
 * </ul>
 * Schalter: {@code -Dcore.gpu.validierung=true} (braucht das Vulkan SDK; verfälscht Zeiten),
 * {@code -Dcore.gpu.geraet=n} wählt das n-te Gerät statt des besten.
 */
public final class Geraet implements AutoCloseable {
    /** Was das Gerät mitbringen soll. */
    private static final String SCHICHT = "VK_LAYER_KHRONOS_validation";

    public static final class Optionen {
        public boolean validierung = Boolean.getBoolean("core.gpu.validierung");
        /** Instanz- und Geräte-Erweiterungen für eine Fläche im Fenster (Swapchain). */
        public boolean flaeche;
        /** Strahlabfrage und Beschleunigungsstrukturen einschalten, wenn das Gerät sie kann. */
        public boolean strahlen = true;
        /** Nummer des Geräts oder −1: das beste (diskret vor integriert vor virtuell vor Software). */
        public int geraet = Integer.getInteger("core.gpu.geraet", -1);
        public String anwendung = "com.dan.core.gpu";
        /**
         * Prüfhilfe für Geräte mit einer Warteschlange: Rechnen und Übertragung bekommen trotzdem eigene Schlangen
         * (Pools, Zeitleiste, Stand der Betriebsmittel), die alle auf derselben Warteschlange abschicken. So läuft der
         * Aufbau der 680M auch auf einem Software-Gerät. Nicht für den Betrieb: Sperren gelten dann je Schlange, obwohl
         * es eine Warteschlange ist; die Prüfung der Synchronisation meldet das.
         */
        public boolean getrennt = Boolean.getBoolean("core.gpu.getrennt");
    }

    /**
     * Was das Gerät kann. {@code float16} heißt: Rechnen in float16 und float16 im Speicherpuffer (beides zusammen);
     * {@code software}: das Gerät rechnet auf dem Prozessor (lavapipe, SwiftShader).
     */
    public record Faehigkeiten(boolean strahlabfrage, boolean deskriptorIndizes, boolean zeitleiste, boolean float16, boolean adressen, boolean sync2,
                               boolean dynamischesRendern, boolean untergruppenArithmetik, int untergruppeMin, int untergruppeMax, boolean zeitstempel,
                               boolean budget, boolean eigeneRechenSchlange, boolean eigeneUebertragung, boolean software) { }

    /** Ein Speicher-Heap: Größe, Budget des Treibers und Belegung in Byte. */
    public record Heap(int nummer, long groesse, long budget, long belegt, boolean geraetelokal, boolean vomProzessorSichtbar) { }

    public final VkInstance instance;
    public final VkPhysicalDevice phys;
    public final VkDevice device;
    public final String name, treiber;
    public final int apiVersion;
    public final Faehigkeiten kann;
    public final Schlange grafik, rechnen, uebertragung;
    /** Die Familien aller Warteschlangen ohne Doppelte – für Puffer und Bilder, die mehrere benutzen. */
    final int[] familien;
    final long vma;
    /** Größte einzelne Zuteilung und größter Speicherpuffer in Byte. */
    public final long groessteZuteilung, groessterPuffer;
    /** Nanosekunden je Zeitstempel-Takt. */
    final float zeitstempelTakt;
    public final Set<String> erweiterungen;
    /** Meldungen der Validierung (höchstens 50) und Zahl der Fehler. */
    public final List<String> meldungen = Collections.synchronizedList(new ArrayList<>());
    public volatile int validierungsFehler;
    public final boolean validierung;
    /**
     * Ob die Validierung auch die Synchronisation prüft (fehlende Sperren). Das geht ab der Schicht 1.4.304: ältere
     * kennen das Warten auf Zeitleisten nicht und melden Fehler, wo keine sind.
     */
    public final boolean synchronPruefung;
    /** Version der Validierungsschicht oder leer. */
    public final String schicht;
    private final Map<String, Konto> konten = new LinkedHashMap<>();
    private long messenger;
    private VkDebugUtilsMessengerCallbackEXT rueckruf;
    private boolean zu;

    public Geraet() { this(new Optionen()); }

    public Geraet(Optionen o) {
        try (MemoryStack s = stackPush()) {
            // ---------------------------------------------------------- Instanz
            int sv = o.validierung ? schichtVersion(SCHICHT) : -1;
            boolean val = sv >= 0, syn = sv >= VK_MAKE_API_VERSION(0, 1, 4, 304) || val && Boolean.getBoolean("core.gpu.synchronErzwingen");
            schicht = val ? version(sv) : "";
            if (o.validierung && !val) System.out.println("  (Validierung gewünscht, aber VK_LAYER_KHRONOS_validation fehlt – Vulkan SDK installieren)");
            validierung = val;
            synchronPruefung = syn;
            List<String> iext = new ArrayList<>();
            boolean einstellungen = val && schichtKann(SCHICHT, EXTLayerSettings.VK_EXT_LAYER_SETTINGS_EXTENSION_NAME);
            if (val) {
                iext.add(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
                if (syn) iext.add(einstellungen ? EXTLayerSettings.VK_EXT_LAYER_SETTINGS_EXTENSION_NAME : EXTValidationFeatures.VK_EXT_VALIDATION_FEATURES_EXTENSION_NAME);
            }
            if (o.flaeche) {
                iext.add(KHRSurface.VK_KHR_SURFACE_EXTENSION_NAME);
                iext.add(Platform.get() == Platform.WINDOWS ? KHRWin32Surface.VK_KHR_WIN32_SURFACE_EXTENSION_NAME : "VK_KHR_xlib_surface");
            }
            VkApplicationInfo app = VkApplicationInfo.calloc(s).sType$Default().pApplicationName(s.UTF8(o.anwendung)).applicationVersion(1)
                    .pEngineName(s.UTF8("com.dan.core.gpu")).engineVersion(1).apiVersion(VK_API_VERSION_1_3);
            VkInstanceCreateInfo ici = VkInstanceCreateInfo.calloc(s).sType$Default().pApplicationInfo(app)
                    .ppEnabledLayerNames(val ? s.pointers(s.UTF8(SCHICHT)) : null).ppEnabledExtensionNames(namen(s, iext));
            // Mit der Schicht auch die Prüfung der Synchronisation: sie findet Zugriffe, zwischen denen eine Sperre fehlt.
            // Zugriffe der Shader auf Puffer zählt sie nur auf Wunsch (neuere Schichten, über ihre Einstellungen).
            if (einstellungen && syn) {
                VkLayerSettingEXT.Buffer es = VkLayerSettingEXT.calloc(2, s);
                String[] ein = {"validate_sync", "syncval_shader_accesses_heuristic"};
                for (int i = 0; i < ein.length; i++)
                    es.get(i).pLayerName(s.UTF8(SCHICHT)).pSettingName(s.UTF8(ein[i])).type(EXTLayerSettings.VK_LAYER_SETTING_TYPE_BOOL32_EXT).valueCount(1)
                            .pValues(s.malloc(4).putInt(0, VK_TRUE));
                ici.pNext(VkLayerSettingsCreateInfoEXT.calloc(s).sType$Default().pSettings(es).address());
            } else if (syn) ici.pNext(VkValidationFeaturesEXT.calloc(s).sType$Default()
                    .pEnabledValidationFeatures(s.ints(EXTValidationFeatures.VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT)).address());
            PointerBuffer pp = s.mallocPointer(1);
            ok(vkCreateInstance(ici, null, pp), "vkCreateInstance");
            instance = new VkInstance(pp.get(0), ici);
            if (val) {
                rueckruf = VkDebugUtilsMessengerCallbackEXT.create((schwere, art, pDaten, nutzer) -> {
                    String m = VkDebugUtilsMessengerCallbackDataEXT.create(pDaten).pMessageString();
                    if ((schwere & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) validierungsFehler++;
                    if (meldungen.size() < 50) meldungen.add(m);
                    return VK_FALSE;
                });
                VkDebugUtilsMessengerCreateInfoEXT dci = VkDebugUtilsMessengerCreateInfoEXT.calloc(s).sType$Default()
                        .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                        .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT).pfnUserCallback(rueckruf);
                LongBuffer lp = s.mallocLong(1);
                ok(vkCreateDebugUtilsMessengerEXT(instance, dci, null, lp), "vkCreateDebugUtilsMessengerEXT");
                messenger = lp.get(0);
            }

            // ---------------------------------------------------------- Gerät wählen
            List<VkPhysicalDevice> alle = geraete(instance);
            if (alle.isEmpty()) throw new IllegalStateException("Kein Vulkan-Gerät gefunden");
            VkPhysicalDevice gew = null;
            if (o.geraet >= 0) {
                if (o.geraet >= alle.size()) throw new IllegalStateException("Gerät " + o.geraet + " gibt es nicht (" + alle.size() + " Geräte)");
                gew = alle.get(o.geraet);
            } else {
                int best = -1;
                for (VkPhysicalDevice d : alle) {
                    VkPhysicalDeviceProperties p = VkPhysicalDeviceProperties.malloc(s);
                    vkGetPhysicalDeviceProperties(d, p);
                    int rang = (p.apiVersion() >= VK_API_VERSION_1_3 ? 10 : 0) + switch (p.deviceType()) {
                        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 4;
                        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 3;
                        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> 2;
                        case VK_PHYSICAL_DEVICE_TYPE_CPU -> 1;
                        default -> 0;
                    };
                    if (rang > best) { best = rang; gew = d; }
                }
            }
            phys = gew;
            VkPhysicalDeviceVulkan11Properties p11 = VkPhysicalDeviceVulkan11Properties.calloc(s).sType$Default();
            VkPhysicalDeviceVulkan12Properties p12 = VkPhysicalDeviceVulkan12Properties.calloc(s).sType$Default();
            VkPhysicalDeviceVulkan13Properties p13 = VkPhysicalDeviceVulkan13Properties.calloc(s).sType$Default();
            VkPhysicalDeviceProperties2 p2 = VkPhysicalDeviceProperties2.calloc(s).sType$Default().pNext(p11.address());
            p11.pNext(p12.address());
            VkPhysicalDeviceProperties prop = VkPhysicalDeviceProperties.malloc(s);
            vkGetPhysicalDeviceProperties(phys, prop);
            name = prop.deviceNameString();
            apiVersion = prop.apiVersion();
            if (apiVersion < VK_API_VERSION_1_3) throw new IllegalStateException(name + " meldet nur Vulkan " + version(apiVersion) + ", gebraucht wird 1.3");
            p12.pNext(p13.address());
            VK11.vkGetPhysicalDeviceProperties2(phys, p2);
            treiber = p12.driverNameString() + " " + p12.driverInfoString();
            zeitstempelTakt = prop.limits().timestampPeriod();
            groessteZuteilung = p11.maxMemoryAllocationSize();
            groessterPuffer = Math.min(p13.maxBufferSize(), Integer.toUnsignedLong(prop.limits().maxStorageBufferRange()));

            // ---------------------------------------------------------- Warteschlangen
            VkQueueFamilyProperties.Buffer fam = familien(phys, s);
            int alleF = -1, rechnenF = -1, transF = -1;
            for (int i = 0; i < fam.capacity(); i++) {
                int f = fam.get(i).queueFlags();
                boolean gr = (f & VK_QUEUE_GRAPHICS_BIT) != 0, re = (f & VK_QUEUE_COMPUTE_BIT) != 0, tr = (f & VK_QUEUE_TRANSFER_BIT) != 0;
                if (gr && re && alleF < 0) alleF = i;
                if (!gr && re && rechnenF < 0) rechnenF = i;
                if (!gr && !re && tr && transF < 0) transF = i;
            }
            if (alleF < 0) throw new IllegalStateException(name + ": keine Familie mit Grafik und Rechnen");

            // ---------------------------------------------------------- Erweiterungen
            Set<String> da = geraeteErweiterungen(phys, s);
            List<String> dext = new ArrayList<>();
            if (o.flaeche) dext.add(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);
            boolean rt = o.strahlen && da.contains(KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME)
                    && da.contains(KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME) && da.contains(KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME);
            if (rt) {
                dext.add(KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME);
                dext.add(KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME);
                dext.add(KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME);
            }
            boolean bud = da.contains(EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME);
            if (bud) dext.add(EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME);
            erweiterungen = Collections.unmodifiableSet(new LinkedHashSet<>(dext));

            // ---------------------------------------------------------- Merkmale: alles, was das Gerät kann
            VkPhysicalDeviceFeatures2 f2 = VkPhysicalDeviceFeatures2.calloc(s).sType$Default();
            VkPhysicalDeviceVulkan11Features f11 = VkPhysicalDeviceVulkan11Features.calloc(s).sType$Default();
            VkPhysicalDeviceVulkan12Features f12 = VkPhysicalDeviceVulkan12Features.calloc(s).sType$Default();
            VkPhysicalDeviceVulkan13Features f13 = VkPhysicalDeviceVulkan13Features.calloc(s).sType$Default();
            VkPhysicalDeviceAccelerationStructureFeaturesKHR fas = VkPhysicalDeviceAccelerationStructureFeaturesKHR.calloc(s).sType$Default();
            VkPhysicalDeviceRayQueryFeaturesKHR frq = VkPhysicalDeviceRayQueryFeaturesKHR.calloc(s).sType$Default();
            f2.pNext(f11.address());
            f11.pNext(f12.address());
            f12.pNext(f13.address());
            if (rt) {
                f13.pNext(fas.address());
                fas.pNext(frq.address());
            }
            VK11.vkGetPhysicalDeviceFeatures2(phys, f2);
            f2.features().robustBufferAccess(false);
            f13.robustImageAccess(false);
            fas.accelerationStructureCaptureReplay(false).accelerationStructureHostCommands(false);
            rt = rt && fas.accelerationStructure() && frq.rayQuery() && f12.bufferDeviceAddress();
            if (!f13.synchronization2() || !f12.timelineSemaphore())
                throw new IllegalStateException(name + ": Synchronisation 2 oder Zeitleisten-Semaphoren fehlen; core.gpu braucht beide");
            boolean indizes = f12.runtimeDescriptorArray() && f12.descriptorBindingPartiallyBound() && f12.descriptorBindingStorageBufferUpdateAfterBind()
                    && f12.descriptorBindingUpdateUnusedWhilePending() && f2.features().shaderStorageBufferArrayDynamicIndexing();
            int ops = p11.subgroupSupportedOperations(), stufen = p11.subgroupSupportedStages();
            boolean arith = (ops & VK11.VK_SUBGROUP_FEATURE_ARITHMETIC_BIT) != 0 && (stufen & VK_SHADER_STAGE_COMPUTE_BIT) != 0;
            kann = new Faehigkeiten(rt, indizes, true, f12.shaderFloat16() && f11.storageBuffer16BitAccess(), f12.bufferDeviceAddress(), true, f13.dynamicRendering(), arith,
                    p13.minSubgroupSize(), p13.maxSubgroupSize(), fam.get(alleF).timestampValidBits() > 0, bud, rechnenF >= 0, transF >= 0,
                    prop.deviceType() == VK_PHYSICAL_DEVICE_TYPE_CPU);

            // ---------------------------------------------------------- Gerät
            int nf = 1 + (rechnenF >= 0 ? 1 : 0) + (transF >= 0 ? 1 : 0);
            VkDeviceQueueCreateInfo.Buffer qci = VkDeviceQueueCreateInfo.calloc(nf, s);
            int q = 0;
            qci.get(q++).sType$Default().queueFamilyIndex(alleF).pQueuePriorities(s.floats(1f));
            if (rechnenF >= 0) qci.get(q++).sType$Default().queueFamilyIndex(rechnenF).pQueuePriorities(s.floats(1f));
            if (transF >= 0) qci.get(q).sType$Default().queueFamilyIndex(transF).pQueuePriorities(s.floats(1f));
            VkDeviceCreateInfo dci2 = VkDeviceCreateInfo.calloc(s).sType$Default().pNext(f2.address()).pQueueCreateInfos(qci).ppEnabledExtensionNames(namen(s, dext));
            ok(vkCreateDevice(phys, dci2, null, pp), "vkCreateDevice");
            device = new VkDevice(pp.get(0), phys, dci2, VK_API_VERSION_1_3);
            grafik = new Schlange(this, "Grafik", alleF, 0, fam.get(alleF).timestampValidBits());
            int bits = fam.get(alleF).timestampValidBits();
            rechnen = rechnenF >= 0 ? new Schlange(this, "Rechnen", rechnenF, 1, fam.get(rechnenF).timestampValidBits())
                    : o.getrennt ? new Schlange(this, "Rechnen (Prüfhilfe)", alleF, 1, bits, grafik) : grafik;
            uebertragung = transF >= 0 ? new Schlange(this, "Übertragung", transF, 2, fam.get(transF).timestampValidBits())
                    : o.getrennt ? new Schlange(this, "Übertragung (Prüfhilfe)", alleF, 2, bits, grafik) : grafik;
            familien = nf == 1 ? new int[] {alleF} : nf == 3 ? new int[] {alleF, rechnenF, transF} : new int[] {alleF, rechnenF >= 0 ? rechnenF : transF};

            // ---------------------------------------------------------- VMA
            VmaVulkanFunctions vf = VmaVulkanFunctions.calloc(s).set(instance, device);
            VmaAllocatorCreateInfo aci = VmaAllocatorCreateInfo.calloc(s)
                    .flags((kann.adressen ? VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT : 0) | (bud ? VMA_ALLOCATOR_CREATE_EXT_MEMORY_BUDGET_BIT : 0))
                    .physicalDevice(phys).device(device).instance(instance).pVulkanFunctions(vf).vulkanApiVersion(VK_API_VERSION_1_3);
            ok(vmaCreateAllocator(aci, pp), "vmaCreateAllocator");
            vma = pp.get(0);
        }
    }

    // ------------------------------------------------------------------ Speicher

    /** Die Heaps des Geräts mit Budget und Belegung. */
    public List<Heap> heaps() {
        try (MemoryStack s = stackPush()) {
            VkPhysicalDeviceMemoryProperties mp = VkPhysicalDeviceMemoryProperties.malloc(s);
            vkGetPhysicalDeviceMemoryProperties(phys, mp);
            VmaBudget.Buffer b = VmaBudget.calloc(VK_MAX_MEMORY_HEAPS, s);
            vmaGetHeapBudgets(vma, b);
            List<Heap> l = new ArrayList<>();
            for (int h = 0; h < mp.memoryHeapCount(); h++) {
                boolean sichtbar = false;
                for (int t = 0; t < mp.memoryTypeCount(); t++)
                    if (mp.memoryTypes(t).heapIndex() == h && (mp.memoryTypes(t).propertyFlags() & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0) sichtbar = true;
                l.add(new Heap(h, mp.memoryHeaps(h).size(), b.get(h).budget(), b.get(h).usage(), (mp.memoryHeaps(h).flags() & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0, sichtbar));
            }
            return l;
        }
    }

    /** Das Konto eines Moduls; beim ersten Ruf mit dieser Grenze (Byte) angelegt, danach gilt die erste Grenze. */
    public synchronized Konto konto(String modul, long grenze) { return konten.computeIfAbsent(modul, m -> new Konto(m, grenze)); }

    /** Alle Konten in der Reihenfolge, in der sie angelegt wurden. */
    public synchronized List<Konto> konten() { return new ArrayList<>(konten.values()); }

    // ------------------------------------------------------------------ Hilfen

    /** Wartet, bis das Gerät nichts mehr zu tun hat. */
    public void ruhe() { vkDeviceWaitIdle(device); }

    /** Ob eine Schicht eine Instanz-Erweiterung anbietet. */
    static boolean schichtKann(String schicht, String erweiterung) {
        try (MemoryStack s = stackPush()) {
            IntBuffer n = s.mallocInt(1);
            if (vkEnumerateInstanceExtensionProperties(schicht, n, null) != VK_SUCCESS || n.get(0) == 0) return false;
            VkExtensionProperties.Buffer e = VkExtensionProperties.malloc(n.get(0));
            try {
                vkEnumerateInstanceExtensionProperties(schicht, n, e);
                for (int i = 0; i < n.get(0); i++) if (e.get(i).extensionNameString().equals(erweiterung)) return true;
                return false;
            } finally {
                e.free();
            }
        }
    }

    /** Version einer Schicht oder −1, wenn es sie nicht gibt. */
    static int schichtVersion(String name) {
        try (MemoryStack s = stackPush()) {
            IntBuffer n = s.mallocInt(1);
            vkEnumerateInstanceLayerProperties(n, null);
            if (n.get(0) == 0) return -1;
            VkLayerProperties.Buffer l = VkLayerProperties.malloc(n.get(0));
            try {
                vkEnumerateInstanceLayerProperties(n, l);
                for (int i = 0; i < n.get(0); i++) if (l.get(i).layerNameString().equals(name)) return l.get(i).specVersion();
                return -1;
            } finally {
                l.free();
            }
        }
    }

    static List<VkPhysicalDevice> geraete(VkInstance inst) {
        try (MemoryStack s = stackPush()) {
            IntBuffer n = s.mallocInt(1);
            ok(vkEnumeratePhysicalDevices(inst, n, null), "vkEnumeratePhysicalDevices");
            List<VkPhysicalDevice> out = new ArrayList<>();
            if (n.get(0) == 0) return out;
            PointerBuffer pp = s.mallocPointer(n.get(0));
            ok(vkEnumeratePhysicalDevices(inst, n, pp), "vkEnumeratePhysicalDevices");
            for (int i = 0; i < pp.capacity(); i++) out.add(new VkPhysicalDevice(pp.get(i), inst));
            return out;
        }
    }

    static VkQueueFamilyProperties.Buffer familien(VkPhysicalDevice d, MemoryStack s) {
        IntBuffer n = s.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(d, n, null);
        VkQueueFamilyProperties.Buffer f = VkQueueFamilyProperties.malloc(n.get(0), s);
        vkGetPhysicalDeviceQueueFamilyProperties(d, n, f);
        return f;
    }

    /** Erweiterungen des Geräts (die Liste liegt auf dem Heap: 150 Einträge zu 260 Byte sprengen den Stapel von LWJGL). */
    static Set<String> geraeteErweiterungen(VkPhysicalDevice d, MemoryStack s) {
        IntBuffer n = s.mallocInt(1);
        ok(vkEnumerateDeviceExtensionProperties(d, (String) null, n, null), "vkEnumerateDeviceExtensionProperties");
        VkExtensionProperties.Buffer e = VkExtensionProperties.malloc(Math.max(1, n.get(0)));
        try {
            ok(vkEnumerateDeviceExtensionProperties(d, (String) null, n, e), "vkEnumerateDeviceExtensionProperties");
            Set<String> out = new LinkedHashSet<>();
            for (int i = 0; i < n.get(0); i++) out.add(e.get(i).extensionNameString());
            return out;
        } finally {
            e.free();
        }
    }

    private static PointerBuffer namen(MemoryStack s, List<String> l) {
        if (l.isEmpty()) return null;
        PointerBuffer pb = s.mallocPointer(l.size());
        for (String x : l) pb.put(s.UTF8(x));
        return pb.flip();
    }

    public static String version(int v) { return VK_API_VERSION_MAJOR(v) + "." + VK_API_VERSION_MINOR(v) + "." + VK_API_VERSION_PATCH(v); }

    static void ok(int r, String was) {
        if (r != VK_SUCCESS) throw new IllegalStateException(was + ": " + fehler(r));
    }

    static String fehler(int r) {
        return switch (r) {
            case VK_NOT_READY -> "VK_NOT_READY";
            case VK_TIMEOUT -> "VK_TIMEOUT";
            case VK_INCOMPLETE -> "VK_INCOMPLETE";
            case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
            case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
            case VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
            case VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
            case VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
            case VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
            case VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
            case VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER (kein Vulkan-1.3-Treiber?)";
            case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
            case VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
            case VK_ERROR_OUT_OF_POOL_MEMORY -> "VK_ERROR_OUT_OF_POOL_MEMORY";
            case KHRSurface.VK_ERROR_SURFACE_LOST_KHR -> "VK_ERROR_SURFACE_LOST_KHR";
            case KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR -> "VK_ERROR_OUT_OF_DATE_KHR";
            case KHRSwapchain.VK_SUBOPTIMAL_KHR -> "VK_SUBOPTIMAL_KHR";
            default -> "Fehler " + r;
        };
    }

    /** Bytes lesbar. */
    public static String mb(long b) { return String.format(java.util.Locale.GERMAN, b >= 10L << 30 ? "%.0f GB" : b >= 1L << 30 ? "%.1f GB" : "%.1f MB", b >= 1L << 30 ? b / 1073741824.0 : b / 1048576.0); }

    /** Gibt alles frei. Puffer, Bilder, Kernels und Zeitleisten müssen vorher geschlossen sein. */
    @Override public void close() {
        if (zu) return;
        zu = true;
        vkDeviceWaitIdle(device);
        grafik.schliessen();
        if (rechnen != grafik) rechnen.schliessen();
        if (uebertragung != grafik) uebertragung.schliessen();
        vmaDestroyAllocator(vma);
        vkDestroyDevice(device, null);
        if (messenger != NULL) vkDestroyDebugUtilsMessengerEXT(instance, messenger, null);
        vkDestroyInstance(instance, null);
        if (rueckruf != null) rueckruf.free();
    }
}
