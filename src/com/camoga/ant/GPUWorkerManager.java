package com.camoga.ant;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;

public class GPUWorkerManager {
    static ArrayList<GPUWorker> workers = new ArrayList<GPUWorker>();
    static int[] numworkers = new int[2]; // 0: normal, 1: hex

    static int idcount;
    private static Integer shaderProgramID = null;
    private static Long glContext = null;

    // TODO do not start new workers until old workers have stopped
    private static void updateWorkers() {
        int[] count = new int[2];
        for (GPUWorker w : workers) {
            count[w.getType()]++;
        }
        for (GPUWorker w : workers) {
            if (count[0] <= numworkers[0])
                break;
            if (w.getType() == 0) {
                w.kill();
                count[0]--;
            }
        }
        for (GPUWorker w : workers) {
            if (count[1] <= numworkers[1])
                break;
            if (w.getType() == 1) {
                w.kill();
                count[1]--;
            }
        }
        for (int i = count[0]; i < numworkers[0]; i++) {
            workers.add(new GPUWorker(idcount++, 0));
        }
        for (int i = count[1]; i < numworkers[1]; i++) {
            workers.add(new GPUWorker(idcount++, 1));
        }
    }

    public static void remove(GPUWorker worker) {
        workers.remove(worker);
    }

    public static void setWorkerType(int type, int num) {
        if (num < 0
                || numworkers[0] + numworkers[1] + numworkers[type] - num > Runtime.getRuntime().availableProcessors())
            throw new RuntimeException();
        numworkers[type] = num;
        updateWorkers();
    }

    public static void setWorkers(int normal, int hex) {
        if (normal < 0 || hex < 0 || normal + hex > Runtime.getRuntime().availableProcessors())
            throw new RuntimeException(
                    "More workers than available processors (" + Runtime.getRuntime().availableProcessors() + ")");
        numworkers[0] = normal;
        numworkers[1] = hex;
        updateWorkers();
    }

    public static void start() {
        if (shaderProgramID == null) {
            setupOpenGLContext();
        }
		for(GPUWorker w : workers) {
			w.start();
		}
    }
    
    private static void setupOpenGLContext() {
        // Setup program & OpenGL
        GLFWErrorCallback errorCallback;
        GLFW.glfwSetErrorCallback(errorCallback = GLFWErrorCallback.createPrint(System.err));
        if (GLFW.glfwInit() != true) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, 0);
        glContext = GLFW.glfwCreateWindow(100, 100, "this should be hidden", 0, 0);
        if (glContext == null) {
            throw new RuntimeException("Failed to create OpenGL context window");
        }
        GLFW.glfwMakeContextCurrent(glContext);
        GL.createCapabilities();

        String source = "";
        try {
            source = new String(Files.readAllBytes(
                    Paths.get(GPUWorkerManager.class.getClassLoader().getResource("ant_compute.glsl").toURI())));
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }

        // Compile shader
        int shader = GL43.glCreateShader(GL43.GL_COMPUTE_SHADER);
        if (shader == 0) throw new RuntimeException("could not create shader object; check ShaderProgram.isSupported()");
        GL43.glShaderSource(shader, source);
        GL43.glCompileShader(shader);
        int comp = GL43.glGetShaderi(shader, GL43.GL_COMPILE_STATUS);
        int len = GL43.glGetShaderi(shader, GL43.GL_INFO_LOG_LENGTH);
        String err = GL43.glGetShaderInfoLog(shader, len);
        String log = "";
        if (err != null && err.length() != 0)
            log += "COMPUTE SHADER " + " compile log:\n" + err + "\n";
        if (comp == GL11.GL_FALSE)
            throw new RuntimeException(log.length()!=0 ? log : "Could not compile compute shader");

        // Create program
        shaderProgramID = GL43.glCreateProgram();

        // Attach shader
        GL43.glAttachShader(shaderProgramID, shader);

        // Link program
        GL43.glLinkProgram(shaderProgramID);
		comp = GL43.glGetProgrami(shaderProgramID, GL43.GL_LINK_STATUS);
		len = GL43.glGetProgrami(shaderProgramID, GL43.GL_INFO_LOG_LENGTH);
		err = GL43.glGetProgramInfoLog(shaderProgramID, len);
		if (err != null && err.length() != 0)
			log = err + "\n" + log;
		if (log != null)
			log = log.trim();
		if (comp == GL11.GL_FALSE)
            throw new RuntimeException(log.length()!=0 ? log : "Could not link program");
        
        System.out.println(log);
    }
	
	public static int size() {
		return numworkers[0]+numworkers[1];
	}
	
	public static int size(int type) {
		return numworkers[type];
    }
    
    public static int getShaderProgramID() {
        return shaderProgramID;
    }

	public static GPUWorker getWorker(int id) {
		if(id < 0 || id >= workers.size()) return null;
		return workers.get(id);
	}
}