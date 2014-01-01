/**
 * This file is part of Client, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2013 Spoutcraft <http://spoutcraft.org/>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spoutcraft.client;

import org.spoutcraft.client.network.Network;
import org.spoutcraft.client.Interface.Interface;
import org.spoutcraft.client.universe.Universe;

/**
 * The game class.
 */
public class Game {
    private final Object wait = new Object();
    private volatile boolean running = false;
    private final Universe universe;
    private final Interface nterface;
    private final Network network;

    static {
        try {
            Class.forName("org.spoutcraft.client.universe.block.material.Materials");
        } catch (Exception ex) {
            System.out.println("Couldn't load the default materials");
        }
    }

    public Game() {
        universe = new Universe(this);
        nterface = new Interface(this);
        network = new Network(this);
    }

    public void start() {
        universe.start();
        nterface.start();
        network.start();
        running = true;
    }

    private void stop() {
        nterface.stop();
        universe.stop();
        network.stop();
        running = false;
    }

    public Universe getUniverse() {
        return universe;
    }

    public Interface getInterface() {
        return nterface;
    }

    public Network getNetwork() {
        return network;
    }

    /**
     * Stops the game and allows any thread waiting on exit (by having called {@link #waitForExit()}) to resume it's activity.
     */
    public void exit() {
        stop();
        synchronized (wait) {
            wait.notifyAll();
        }
    }

    /**
     * Returns true if the game is running, false if otherwise.
     *
     * @return Whether or not the game is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Causes the current thread to wait until the {@link #exit()} method is called.
     *
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    public void waitForExit() throws InterruptedException {
        synchronized (wait) {
            while (isRunning()) {
                wait.wait();
            }
        }
    }
}
