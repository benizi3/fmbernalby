package com.virjar.hermes.hermesagent.util.libsuperuser;
/*
 * Copyright (C) 2012-2015 Jorrit "Chainfire" Jongma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.os.Looper;


/**
 * Utility class for logging and debug features that (by default) does nothing when not in debug mode
 */
public class Debug {

    // ----- DEBUGGING -----

    /**
     * <p>Is debug mode enabled ?</p>
     *
     * @return Debug mode enabled
     */
    public static boolean getDebug() {
        return false;
    }

    // ----- LOGGING -----

    public static final int LOG_COMMAND = 0x0002;
    public static final int LOG_OUTPUT = 0x0004;


    /**
     * <p>Enable or disable logging specific types of message</p>
     * <p>
     * <p>You may | (or) LOG_* constants together. Note that
     * debug mode must also be enabled for actual logging to
     * occur.</p>
     *
     * @param type   LOG_* constants
     * @param enable Enable or disable
     */
    public static void setLogTypeEnabled(int type, boolean enable) {
    }

    // ----- SANITY CHECKS -----

    /**
     * <p>Are sanity checks enabled ?</p>
     * <p>
     * <p>Note that debug mode must also be enabled for actual
     * sanity checks to occur.</p>
     *
     * @return True if enabled
     */
    public static boolean getSanityChecksEnabled() {
        return true;
    }

    /**
     * <p>Are sanity checks enabled ?</p>
     * <p>
     * <p>Takes debug mode into account for the result.</p>
     *
     * @return True if enabled
     */
    public static boolean getSanityChecksEnabledEffective() {
        return getDebug() && getSanityChecksEnabled();
    }

    /**
     * <p>Are we running on the main thread ?</p>
     *
     * @return Running on main thread ?
     */
    public static boolean onMainThread() {
        return ((Looper.myLooper() != null) && (Looper.myLooper() == Looper.getMainLooper()));
    }

}
