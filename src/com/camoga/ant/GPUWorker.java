package com.camoga.ant;

import java.nio.ByteBuffer;

import com.camoga.ant.net.Client;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
public class GPUWorker {
    private int workerID;
    private int type;
    private int rulesSSBO;
    private int resSSBO;
    private int nAnts;
    private ByteBuffer rulesBuffer = null;
    private ByteBuffer resBuffer = null;
    private boolean kill;
    private boolean running;
    private Thread thread;

    public GPUWorker(int ID, int pType) {
        // Create buffers
        this.nAnts = 128;
        this.workerID = ID;
        this.type = pType;
        this.rulesBuffer= ByteBuffer.allocateDirect(this.nAnts * 2);
        this.resBuffer = ByteBuffer.allocateDirect(this.nAnts);
    }

    public void start() {
        if(running) return;

        this.rulesSSBO = GL15.glGenBuffers();
        this.resSSBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.rulesSSBO);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, this.nAnts * 2, 0);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.resSSBO);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, this.nAnts, 0);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

		thread = new Thread(() -> run(), "AntWorker"+workerID+" (GPU)");
		thread.start();
		Client.LOG.info("Worker " + workerID + " (GPU)" + " started");
		running = true;
    }

    public void run() {
        if (this.kill) {
            return;
        }

        long[] rules = Client.getRules(0, this.nAnts);

        // Write rules to GPU
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.rulesSSBO);
        GL43.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.nAnts * 2, GL43.GL_WRITE_ONLY, this.rulesBuffer);
        for (int i = 0; i < this.nAnts; i++) {
            this.rulesBuffer.putInt(2 * i, ((int) rules[i]) & 0xffffffff);
            this.rulesBuffer.putInt(2* i + 1, (int) ((rules[i] & 0xffffffff00000000L) >>> 32));
        }
        GL43.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);

        // Bind buffers to shader
        GL43.glUseProgram(GPUWorkerManager.getShaderProgramID());
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, this.rulesSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, this.resSSBO);

        // Run shader
        GL43.glDispatchCompute(this.nAnts / 32, 1, 1);

        // Get results back
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.resSSBO);
        GL43.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.nAnts, GL43.GL_READ_ONLY, this.resBuffer);
    }

    public int getType() {
        return this.type;
    }

    public void kill() {
		kill = true;
	}
}