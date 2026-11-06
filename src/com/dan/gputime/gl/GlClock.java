package com.dan.gputime.gl;

import com.dan.gputime.GpuSpans;
import com.jogamp.opengl.GL4;

/** Zeitstempel der Grafikkarte (GL_TIMESTAMP) für {@link GpuSpans}; seit LituyaBay1958 W6 Runde 6. Nur im GL-Faden. */
public final class GlClock implements GpuSpans.Clock {
    private GL4 gl;
    private final int[] one = new int[1];
    private final long[] val = new long[1];

    /** Den Kontext des laufenden Bilds setzen (vor den Abschnitten jedes Bilds; JOGL kann das Objekt tauschen). */
    public GlClock use(GL4 gl) { this.gl = gl; return this; }

    @Override public int create() { gl.glGenQueries(1, one, 0); return one[0]; }
    @Override public void stamp(int id) { gl.glQueryCounter(id, GL4.GL_TIMESTAMP); }
    @Override public boolean ready(int id) { gl.glGetQueryObjectiv(id, GL4.GL_QUERY_RESULT_AVAILABLE, one, 0); return one[0] != 0; }
    @Override public long nanos(int id) { gl.glGetQueryObjecti64v(id, GL4.GL_QUERY_RESULT, val, 0); return val[0]; }
    @Override public void delete(int[] ids) { if (ids.length > 0) gl.glDeleteQueries(ids.length, ids, 0); }
}
