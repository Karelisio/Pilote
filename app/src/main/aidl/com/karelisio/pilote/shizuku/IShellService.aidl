package com.karelisio.pilote.shizuku;

interface IShellService {
    /** Reserved transaction code used by Shizuku to stop the user service. */
    void destroy() = 16777114;

    /** Runs a shell command; returns "<exitCode>\n<output>". */
    String exec(String command) = 1;
}
