package com.camoga.ant;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import com.camoga.ant.net.Client;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;

public class GPUWorker {
    private int workerID;
    private int type;
    private int stateSSBO;
    private int chunkSSBO;
    private int resSSBO;
    private int dirSSBO;
    private int ruleSizeSSBO;
    private int xySSBO;
    private int nAnts;
    private boolean kill = false;
    private boolean running = false;
    private Thread thread;

    public GPUWorker(int ID, int pType) {
        // Create buffers
        this.nAnts = 16;
        this.workerID = ID;
        this.type = pType;
    }

    public void start() {
        if(running) return;
        
        this.stateSSBO = GL15.glGenBuffers();
        this.chunkSSBO = GL15.glGenBuffers();
        this.resSSBO = GL15.glGenBuffers();
        this.dirSSBO = GL15.glGenBuffers();
        this.ruleSizeSSBO = GL15.glGenBuffers();
        this.xySSBO = GL15.glGenBuffers();

        // State info
        int[] auxArr = new int[this.nAnts * 64];
        Arrays.fill(auxArr, 0);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.stateSSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, IntBuffer.wrap(auxArr), GL44.GL_MAP_READ_BIT | GL44.GL_MAP_WRITE_BIT);
        auxArr = null;

        // Chunks info
        auxArr = new int[this.nAnts * 2048 * 2048];
        Arrays.fill(auxArr, 0);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.chunkSSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, IntBuffer.wrap(auxArr), GL44.GL_MAP_READ_BIT);
        auxArr = null;

        // Result info
        auxArr = new int[this.nAnts];
        Arrays.fill(auxArr, 0);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.resSSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, IntBuffer.wrap(auxArr), GL44.GL_MAP_READ_BIT);

        // Direction info
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.dirSSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, IntBuffer.wrap(auxArr), GL44.GL_MAP_READ_BIT);

        // Rule size info
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.ruleSizeSSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, IntBuffer.wrap(auxArr), GL44.GL_MAP_READ_BIT | GL44.GL_MAP_WRITE_BIT);
        auxArr = null; // Was reused for direction, result and rule size buffers

        // Position info
        auxArr = new int[this.nAnts * 2];
        Arrays.fill(auxArr, 2048 / 2);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.xySSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, IntBuffer.wrap(auxArr), GL44.GL_MAP_READ_BIT);
        auxArr = null;

        // TODO
		//thread = new Thread(() -> run(), "AntWorker"+workerID+" (GPU)");
        //thread.start();
        run();
		Client.LOG.info("Worker " + workerID + " (GPU)" + " started");
		running = true;
    }

    public void run() {
        if (this.kill) {
            return;
        }

        //long[] rules = Client.getRules(0, this.nAnts);

        long[] rules = new long[this.nAnts];
        for (int i = 1; i <= this.nAnts; i++) {
            rules[i-1] = i + 32;
        }

        // Write state to GPU
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.stateSSBO);
        var stateBuffer = GL43.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL43.GL_WRITE_ONLY);
        var ruleSizeList = new ArrayList<Integer>();
        stateBuffer.position(0);
        for (int i = 0; i < this.nAnts; i++) {
            int ruleLow = ((int) rules[i]) & 0xffffffff;
            int ruleHigh = (int) ((rules[i] & 0xffffffff00000000L) >>> 32);
            int ruleSize = 0;
            for (int j = 0; ruleLow != 0; j++) {
                stateBuffer.putInt(j, ((ruleLow & 1) == 1) ? 1 : 3);
                ruleLow /= 2;
                ruleSize++;
            }
            if (ruleHigh != 0) {
                ruleSize = 32;
            }
            for (int j = ruleSize; ruleHigh != 0; j++) {
                stateBuffer.putInt(j, ((ruleHigh & 1) == 1) ? 1 : 3);
                ruleHigh /= 2;
                ruleSize++;
            }
            ruleSizeList.add(ruleSize);
        }
        GL43.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
        GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        stateBuffer = null;

        // Write rules size to GPU
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.ruleSizeSSBO);
        var ruleSizeBuffer = GL43.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL43.GL_WRITE_ONLY);
        for (int i = 0; i < ruleSizeList.size(); i++) {
            ruleSizeBuffer.putInt(i, ruleSizeList.get(i));
        }
        GL43.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
        GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        // Bind buffers to shader
        GL43.glUseProgram(GPUWorkerManager.getShaderProgramID());
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, this.stateSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, this.chunkSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, this.resSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, this.dirSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, this.ruleSizeSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 5, this.xySSBO);

        // Run shader
        final int SHADER_PASSES = 20;
        for (int i = 0; i < SHADER_PASSES; i++) {
            GL43.glDispatchCompute((int) Math.ceil(this.nAnts / 32.0), 1, 1);
            GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        }
        
        // Get results back
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.resSSBO);
        var resBuffer = GL43.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL43.GL_READ_ONLY);
        resBuffer.position(0);
        for (int i = 0; i < this.nAnts; i++) {
            var res = resBuffer.getInt();
            //if (res == 1)
        	    System.out.println("rule " + rules[i] + " -> " + res);
        }
        GL43.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
        resBuffer = null;
    }

    public int getType() {
        return this.type;
    }

    public void kill() {
		kill = true;
	}
}