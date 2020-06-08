package com.camoga.ant;

import java.nio.ByteBuffer;

import com.camoga.ant.net.Client;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
public class GPUWorker {
    private int workerID;
    private int type;
    private int rulesSSBO;
    private int chunkSSBO;
    private int resSSBO;
    private int nAnts;
    private ByteBuffer rulesBuffer = null;
    private ByteBuffer resBuffer = null;
    private boolean kill = false;
    private boolean running = false;
    private Thread thread;

    public GPUWorker(int ID, int pType) {
        // Create buffers
        this.nAnts = 16;
        this.workerID = ID;
        this.type = pType;
        this.rulesBuffer = BufferUtils.createByteBuffer(this.nAnts * 2 * 4); // 8 bytes per ant (2 int per ant)
        this.resBuffer = BufferUtils.createByteBuffer(this.nAnts * 4); // 4 bytes per ant (1 int per ant)
    }

    public void start() {
        if(running) return;
        
        this.rulesSSBO = GL15.glGenBuffers();
        this.resSSBO = GL15.glGenBuffers();
        this.chunkSSBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.rulesSSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, this.nAnts * 2 * 4, GL44.GL_MAP_WRITE_BIT);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.chunkSSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, this.nAnts * 4*2048*2048, GL44.GL_MAP_READ_BIT | GL44.GL_MAP_WRITE_BIT);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.resSSBO);
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, this.nAnts * 4, GL44.GL_MAP_READ_BIT);

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

        // Write rules to GPU
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.rulesSSBO);
        this.rulesBuffer = GL43.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL43.GL_WRITE_ONLY);
        // low and high
        this.rulesBuffer.clear();
        this.rulesBuffer.position(0);
        for (int i = 0; i < this.nAnts; i++) {
            this.rulesBuffer.putInt(((int) rules[i]) & 0xffffffff);
            this.rulesBuffer.putInt((int) ((rules[i] & 0xffffffff00000000L) >>> 32));
        }

        //this.rulesBuffer.position(0);
        //for (int i = 0; i < this.nAnts; i++) {
        //    System.out.println("ruletest " + (i + 1) + " => " + (this.rulesBuffer.getInt() | (this.rulesBuffer.getInt() << 32)));
        //}

        GL43.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
        GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        // Bind buffers to shader
        GL43.glUseProgram(GPUWorkerManager.getShaderProgramID());
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, this.rulesSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, this.chunkSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, this.resSSBO);

        // Run shader
        GL43.glDispatchCompute((int) Math.ceil(this.nAnts / 32.0), 1, 1);

        // Get results back
        GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.resSSBO);
        this.resBuffer = GL43.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL43.GL_READ_ONLY);
        this.resBuffer.position(0);
        for (int i = 0; i < this.nAnts; i++) {
            var res = resBuffer.getInt();
            if (res == 1)
        	    System.out.println("rule " + rules[i]);
        }
        GL43.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
    }

    public int getType() {
        return this.type;
    }

    public void kill() {
		kill = true;
	}
}