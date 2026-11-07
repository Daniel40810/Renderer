#version 450
flat in int vM;
void main() { if (vM == 10) discard; }   // Schriftfelder werfen keinen Schatten
