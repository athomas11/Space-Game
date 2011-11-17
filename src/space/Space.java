
/**
 * This program uses openGL through the Java OpenGL (JOGL)
 * libraries.  It creates a robot with independent points
 * of rotation at all arm and leg joints, as well as at
 * the hinge of the jaw, and at the bottom of the torso.
 * Each joint is randomly assigned an update value which
 * controls the change in the joint angle during each
 * update.  This ensures that all joints rotate independently.
 * The program can be stopped by pressing the escape key or
 * closing the window.
 */
package space;
import java.io.InputStream;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.*;
import javax.swing.JFrame;
import javax.swing.event.MouseInputAdapter;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.texture.TextureIO;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.*;
import com.jogamp.opengl.util.texture.spi.awt.IIOTextureProvider;




public class Space extends JFrame implements GLEventListener {

	private static final long serialVersionUID = 1L;
	private static final double DEGREES_PER_PIXEL = 360.0/800.0;
	private static float ZOOM_DELTA;
        float LEFT = 0;
        float RIGHT = 0;
        float FORWARD = 0;
        float BACK = 0;
	public static void main (final String[] args){
		float A = 100.0f;
		float B = 40.0f;
		int P = 1;
		int Q = 7;
		int N = 100;
		int M = 32;
		float R = 20.0f;

		for(int i = 0; i < args.length; i +=2){
			String param = args[i];
			if(i+1 >= args.length){
				System.out.println("Error: illegal number of arguments");
				System.exit(1);
			}
			try{
				if(param.equals("-a")){
					A = Float.parseFloat(args[i+1]);
				} else if(param.equals("-b")){
					B = Float.parseFloat(args[i+1]);
				} else if(param.equals("-r")){
					R = Float.parseFloat(args[i+1]);
				} else if(param.equals("-q")){
					Q = Integer.parseInt(args[i+1]);
				} else if(param.equals("-p")){
					P = Integer.parseInt(args[i+1]);
				} else if(param.equals("-n")){
					N = Integer.parseInt(args[i+1]);
				} else if(param.equals("-m")){
					M = Integer.parseInt(args[i+1]);
				} else {
					System.out.println("Error: invalid argument '" + param + "'");
					System.exit(1);
				}
			}catch (NumberFormatException e){
				System.out.println("Illegal number '" + args[i+1] + "'");
				System.exit(1);
			}
		}

		final Space app = new Space(A, B, P, Q, N, M, R);

		app.run();
	}
	private float A;
	private float B;
	private int P;
	private int Q;
	private int N;
	private int M;
	private float R;
	private float maxPosition;
	private int mousex;
	private int mousey;
	private double dx;
	private double dy;

	// FPSAnimator performs animation by repeatedly calling
	// the display method
	private FPSAnimator animator;

    // light properties
    private final float[] lightAmbient = {0.5f, 0.5f, 0.5f, 1.0f};
    private final float[] lightDiffuse = {1.0f, 1.0f, 1.0f, 1.0f};
    private final float[] lightSpecular = {1.0f, 1.0f, 1.0f, 1.0f};
    private final float[] lightPosition = {0.0f, 0.0f, 1.0f, 1.0f};
    // material properties
    private final float[] materialAmbient = {0.7f, 0.7f, 0.7f, 0.7f};
    private final float[] materialDiffuse = {0.1f, .3f, 0.3f, 1.0f};
    private final float[] materialSpecular = {0.5f, 0.5f, 0.5f, 1.0f};
	private final float[] materialShininess = {60.0f};
	// camera position
	private final float[] cameraPosition;

	private int[] vertexBufferObjects;
	private GLU glu;
	private final int width;
	private int height;

	private double[] spineVertices;
	private double[] frameVertices;
	private double[] surfaceNormals;
	private int[] tubeStrips;
	private int vertexShader;
	private int fragmentShader;
	private int drawType = 2;

	private double[] colors;
	private double rotatex;
	private double rotatey;
        private Texture earthTexture;
        GLProfile glp = GLProfile.getDefault();

	public Space(float a, float b, int p, int q, int n, int m, float r){
		super("Space");
		width = height = 800;
		A = a;
		B = b;
		P = p;
		Q = q;
		N = n;
		M = m;
		R = r;
		maxPosition = 15.0f*(A+B);
		ZOOM_DELTA = maxPosition/100.0f;
		rotatey = -45.0f;
		cameraPosition = new float[]{0.0f, 0.0f, maxPosition/3};
	}

	public void centerWindow(final Component frame){
		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final Dimension frameSize = frame.getSize();

		if(frameSize.width > screenSize.width){
			frameSize.width = screenSize.width;
		}
		if(frameSize.height > screenSize.height){
			frameSize.height = screenSize.height;
		}

		frame.setLocation(
			(screenSize.width - frameSize.width) >> 1,
			(screenSize.height - frameSize.height) >> 1
		);
	}


	private double[] cross(final double[] vecA, final double[] vecB) {
		final double[] cross = new double[3];
		cross[0] = vecA[1]*vecB[2] - vecA[2]*vecB[1];
		cross[1] = vecA[2]*vecB[0] - vecA[0]*vecB[2];
		cross[2] = vecA[0]*vecB[1] - vecA[1]*vecB[0];
		return cross;
	}

	private double ddx(final double t) {
		return -P*dy(t) + B*Q*(P*Math.sin(Q*t)*Math.sin(P*t) - Q*Math.cos(Q*t)*Math.cos(P*t));
	}

	private double ddy(final double t) {
		return P*dx(t) - B*Q*(P*Math.sin(Q*t)*Math.cos(P*t) + Q*Math.cos(Q*t)*Math.sin(P*t));
	}

	private double ddz(final double t) {
		return -(Q*Q)*B*Math.sin(Q*t);
	}


	/* (non-Javadoc)
	 * @see javax.media.opengl.GLEventListener#display(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public void display(final GLAutoDrawable glDrawable) {
		final GL2 gl = glDrawable.getGL().getGL2();
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
		// set camera position/direction
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		glu.gluLookAt(cameraPosition[0], cameraPosition[1], cameraPosition[2], // camera position
				cameraPosition[0], cameraPosition[1], 0.0f, 	// look at position
				0.0f, 1.0f, 0.0f);	// up direction
                
		gl.glPushMatrix();
		gl.glRotated(rotatex, 0.0, 1.0, 0.0);
		gl.glRotated(rotatey, 1.0, 0.0, 0.0);
		if(drawType == 0){
			drawSpine(gl);
		} else {
                        GLUquadric SOLID = glu.gluNewQuadric();
                        GLUquadric stars = glu.gluNewQuadric();
                        glu.gluQuadricDrawStyle(stars, GLU.GLU_POINT);
                        gl.glPushMatrix();
                        glu.gluQuadricTexture(SOLID, true);
                        earthTexture.enable(gl);
                        earthTexture.bind(gl);
                        gl.glPushMatrix();
                        drawPlanet(gl, SOLID);
                        gl.glPopMatrix();
                        earthTexture.disable(gl);
                        gl.glPopMatrix();
                        gl.glPushMatrix();
                        gl.glColor3f(1,0,0);
                        drawAsteroid(gl, SOLID);
                        gl.glPopMatrix();
                        gl.glPushMatrix();
                        drawShip(gl, SOLID);
                        gl.glPopMatrix();
                        gl.glPushMatrix();
                        drawAlienShip(gl, SOLID);
                        gl.glPopMatrix();
                }
		gl.glPopMatrix();
		glDrawable.swapBuffers();
	}
        public void drawShip(final GL2 gl, GLUquadric SOLID){
                        glu.gluQuadricDrawStyle( SOLID, GLU.GLU_FILL);
                        glu.gluQuadricNormals( SOLID, GLU.GLU_SMOOTH );
                        gl.glTranslatef(200, 0, 0);
                        gl.glTranslatef(FORWARD, LEFT, RIGHT); 
                        
                        gl.glPushMatrix();
                        gl.glRotated(90, 1, 0, 0);
                        gl.glRotated(-90, 0, 1, 0);
                        gl.glPushMatrix();
                        gl.glTranslatef(0,0,8);
                        glu.gluCylinder(SOLID, 5, 5, 15, 10, 10);
                        gl.glTranslatef(0, 0, 15);
                        glu.gluDisk(SOLID, 0, 5, 10, 10);
                        gl.glPushMatrix();
                        /*gl.glBegin(GL.GL_TRIANGLES);        // Drawing Using Triangles
                        gl.glVertex3f(0.0f, -15.0f, 0.0f);
                        gl.glVertex3f(0.0f, 0.0f, -22.0f);
                        gl.glVertex3f(0.0f, 15.0f, 10.0f);
                        gl.glEnd();*/                         // Finished Drawing The Triangle
                        gl.glPopMatrix();
                        gl.glPopMatrix();
                        glu.gluCylinder(SOLID, 0, 5, 8, 10, 10);
                        gl.glPopMatrix();
                        
        }
        public void drawPlanet(final GL2 gl, GLUquadric SOLID){
                        glu.gluQuadricDrawStyle( SOLID, GLU.GLU_FILL);
                        glu.gluQuadricNormals( SOLID, GLU.GLU_SMOOTH );
                        
                        glu.gluQuadricDrawStyle( SOLID, GLU.GLU_FILL);
                        glu.gluQuadricNormals( SOLID, GLU.GLU_SMOOTH );
                        //Axis of Planet
                        gl.glBegin(GL.GL_LINE_LOOP);
                        gl.glVertex3f(0, 0, 0);
                        gl.glVertex3f(400, 0, 0);
                        gl.glEnd();
                        gl.glBegin(GL.GL_LINE_LOOP);
                        gl.glVertex3f(0, 0, 0);
                        gl.glVertex3f(0, 400, 0);
                        gl.glEnd();
                        gl.glBegin(GL.GL_LINE_LOOP);
                        gl.glVertex3f(0, 0, 0);
                        gl.glVertex3f(0, 0, 400);
                        gl.glEnd();
                        glu.gluSphere(SOLID, 100f, 50, 50);
                        
        }

        public void drawAlienShip(final GL2 gl, GLUquadric SOLID){
            glu.gluQuadricDrawStyle( SOLID, GLU.GLU_FILL);
            glu.gluQuadricNormals( SOLID, GLU.GLU_SMOOTH );
            gl.glTranslatef(0f,200f,0f);
            gl.glPushMatrix();
            gl.glBegin(GL.GL_TRIANGLES);
            gl.glVertex3f(0,-15,0);
            gl.glVertex3f(0,0,15);
            gl.glVertex3f(0,15,0);
            gl.glColor4f(1,0,0,1);
            gl.glVertex3f(0,-15,0);
            gl.glVertex3f(0,0,15);
            gl.glVertex3f(10,-15,0);
            
            gl.glVertex3f(0,15,0);
            gl.glVertex3f(0,0,15);
            gl.glVertex3f(10,15,0);
            
            gl.glVertex3f(10,-15,0);
            gl.glVertex3f(0,0,15);
            gl.glVertex3f(10,15,0);
            //NEED BACK SIDE OF SHIP
            
            gl.glEnd();
            gl.glPopMatrix();
        }
        public void drawAsteroid(final GL2 gl, GLUquadric SOLID){
            gl.glTranslatef(250f,-300f,0f);
            for(int i = -5; i < 5; i++){
                
                gl.glTranslatef(0f,50f,0f);
                //gl.glRotated(i*10,0,1,1);
                glu.gluSphere(SOLID, 8f, 5, 5);
            }
            
        }
        
        
        
	public void displayChanged(final GLAutoDrawable glDrawable, final boolean modeChanged, final boolean deviceChanged){
		glDrawable.getGL().getGL2().glViewport(0, 0, getWidth(), getHeight());
		display(glDrawable);
	}

	/* (non-Javadoc)
	 * @see javax.media.opengl.GLEventListener#dispose(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public void dispose(final GLAutoDrawable glDrawable) {
	}


	private float dot(final double[] vec1, final float[] vec2) {
		float sum = 0.0f;
		for(int i = 0; i < vec1.length; i++){
			sum += vec1[i]*vec2[i];
		}
		return sum;
	}


	/**
	 * @param gl
	 */
	private void drawSpine(final GL2 gl) {
		gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
		// draw spine
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vertexBufferObjects[0]);
		gl.glVertexPointer(3, GL2.GL_DOUBLE, 0, 0);
		gl.glDrawArrays(GL2.GL_LINE_LOOP, 0, N);
                
	}

	private void drawToroid(final GL2 gl) {
		gl.glPushMatrix();

		// bind buffer containing vertices
		gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vertexBufferObjects[1]);
		gl.glVertexPointer(3, GL2.GL_DOUBLE, 0, 0);

		// bind buffer containing indices
		gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, vertexBufferObjects[2]);


		// bind buffer containing normals
		gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vertexBufferObjects[3]);
		gl.glNormalPointer(GL2.GL_DOUBLE, 0, 0);


		if(drawType == 1){
			// wire frame mode
			gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_LINE);
		} else {
			// filled mode
			gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_FILL);
		}

		final int numStripVerts = 2*M+2;
		for(int i = 0; i < N; i++){
			gl.glDrawElements(GL2.GL_QUAD_STRIP, numStripVerts, GL2.GL_UNSIGNED_INT, i*numStripVerts*Buffers.SIZEOF_INT);
		}
		gl.glPopMatrix();
	}

	private double dx(final double t) {
		return -P*y(t) - B*Q*Math.sin(Q*t)*Math.cos(P*t);
	}

	private double dy(final double t) {
		return P*x(t) - B*Q*Math.sin(Q*t)*Math.sin(P*t);
	}


	private double dz(final double t) {
		return B*Q*Math.cos(Q*t);
	}

	/* (non-Javadoc)
	 * @see javax.media.opengl.GLEventListener#init(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public void init(final GLAutoDrawable glDrawable) {
		glu = new GLU();
                
		final GL2 gl = glDrawable.getGL().getGL2();
		gl.glShadeModel(GL2.GL_SMOOTH);
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	    gl.glEnable(GL2.GL_DEPTH_TEST);
	    gl.glDepthFunc(GL2.GL_LEQUAL);
		// set material properties
		gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT, materialAmbient, 0);
		gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, materialDiffuse, 0);
		gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, materialSpecular, 0);
		gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, materialShininess, 0);

		// set light properties
		gl.glEnable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_LIGHT0);
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, lightAmbient, 0);
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, lightDiffuse, 0);
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, lightSpecular, 0);
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPosition, 0);

                
		gl.glPointSize(1.0f);
		gl.glLineWidth(1.0f);
                
                try {
                    
                    InputStream stream = getClass().getResourceAsStream("moonmap1k.png");
                    TextureData data = TextureIO.newTextureData(glp, stream, false, "png");
                    //TextureData data2 = TextureIO.newTextureData(null, stream, false, "png");  
                    //data = TextureIO.newTextureData(gl, stream, false, "png");
                    earthTexture = TextureIO.newTexture(data);
                }
                catch (IOException exc) {
                    exc.printStackTrace();
                    System.exit(1);
                }
                
		loadToroid(gl);
	}


	private void loadShaders(final GL2 gl) {
		vertexShader = gl.glCreateShader(GL2.GL_VERTEX_SHADER);
		fragmentShader = gl.glCreateShader(GL2.GL_FRAGMENT_SHADER);
		String vSource = "";
		String fSource = "";
		try {
			final BufferedReader vBuff = new BufferedReader(new FileReader("vertex.glsl"));
			String vLine;
			while((vLine = vBuff.readLine()) != null){
				vSource += vLine + "\n";
			}

			final BufferedReader fBuff = new BufferedReader(new FileReader("fragment.glsl"));
			String fLine;
			while((fLine = fBuff.readLine()) != null){
				fSource += fLine + "\n";
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		// compile vertex shader
		gl.glShaderSource(vertexShader, 1, new String[]{vSource}, (int[])null, 0);
		gl.glCompileShader(vertexShader);
		// compile fragment shader
		gl.glShaderSource(fragmentShader, 1, new String[]{fSource}, (int[])null, 0);
		gl.glCompileShader(fragmentShader);

		// create shader program
		final int shaderprogram = gl.glCreateProgram();
		// attach shaders
		gl.glAttachShader(shaderprogram, vertexShader);
		gl.glAttachShader(shaderprogram, fragmentShader);

		gl.glLinkProgram(shaderprogram);
		gl.glValidateProgram(shaderprogram);
		gl.glUseProgram(shaderprogram);
	}

	/**
	 * @param gl
	 */
	private void loadToroid(final GL2 gl) {
		loadVertices();
//		printVertices();
//		printNormals();
		loadVBOs(gl);
                
                
	}

	private void loadVBOs(final GL2 gl) {
		vertexBufferObjects = new int[4];
		gl.glGenBuffers(4, vertexBufferObjects, 0);
		// spine vertices
		final DoubleBuffer toroidBuff = DoubleBuffer.allocate(spineVertices.length);
		toroidBuff.put(spineVertices);
		toroidBuff.rewind();
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBufferObjects[0]); // bind vertex buffer
		gl.glBufferData(GL.GL_ARRAY_BUFFER, spineVertices.length*Buffers.SIZEOF_DOUBLE, toroidBuff, GL.GL_STATIC_DRAW);

		// Frenet frame vertices
		final DoubleBuffer frameBuff = DoubleBuffer.allocate(frameVertices.length);
		frameBuff.put(frameVertices);
		frameBuff.rewind();
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBufferObjects[1]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, frameVertices.length*Buffers.SIZEOF_DOUBLE, frameBuff, GL.GL_STATIC_DRAW);

		// load vertex array indices
		final IntBuffer frameIndexBuff = IntBuffer.allocate(tubeStrips.length);
		frameIndexBuff.put(tubeStrips);
		frameIndexBuff.rewind();
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, vertexBufferObjects[2]);
		gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, tubeStrips.length*Buffers.SIZEOF_INT, frameIndexBuff, GL.GL_STATIC_DRAW);

		// load vertex normal values
		DoubleBuffer normalBuff = DoubleBuffer.allocate(surfaceNormals.length);
		normalBuff.put(surfaceNormals);
		normalBuff.rewind();
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBufferObjects[3]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, surfaceNormals.length*Buffers.SIZEOF_DOUBLE, normalBuff, GL.GL_STATIC_DRAW);
                
	}

	/**
	 * @param i
	 * @param j
	 * @param k
	 * @param l
	 * @return
	 */
	private void loadVertices() {
		spineVertices = new double[N*3];
		frameVertices = new double[N*M*3];
		surfaceNormals = new double[N*M*3];
		tubeStrips = new int[N*(2*M+2)];

		final double dt = 2*Math.PI/N;
		final double du = 2*Math.PI/M;
		// load the vertices
		double t = 0.0;
		for(int i = 0; i < N; ++i, t+= dt){
			// center spine
			final double[] centerPoint = new double[3];
			centerPoint[0] = spineVertices[i*3] = x(t);
			centerPoint[1] = spineVertices[i*3+1] = y(t);
			centerPoint[2] = spineVertices[i*3+2] = z(t);

			// load tangent vector
			double[] tangent = new double[3];
			tangent[0] = dx(t);
			tangent[1] = dy(t);
			tangent[2] = dz(t);

			// load acceleration vector
			final double[] acceleration = new double[3];
			acceleration[0] = ddx(t);
			acceleration[1] = ddy(t);
			acceleration[2] = ddz(t);

			// load binormal vector
			double[] binormal = cross(tangent, acceleration);

			// normalize vectors
			tangent = normalize(tangent);
			binormal = normalize(binormal);

			// load normal vector
			final double[] normal = cross(binormal, tangent);

			final int iOffset = i*M*3;
			// load next Frenet frame
			double u = 0.0;
			for(int j = 0; j < M; j++, u+=du){
				final int jOffset = j*3;
				for(int k = 0; k < 3; k++){
					surfaceNormals[iOffset+jOffset+k] = Math.cos(u)*binormal[k] + Math.sin(u)*normal[k];
					frameVertices[iOffset+jOffset+k] = centerPoint[k] + R*surfaceNormals[iOffset+jOffset+k];
				}
			}
		}
		// create mesh quad strip indices
		int n = 0;
		for(int i = 0; i < N; i++){
			for(int j = 0; j < M; j++){
				tubeStrips[n++] = vertexIndex((i+1)%N, j);
				tubeStrips[n++] = vertexIndex(i, j);
			}
			tubeStrips[n++] = vertexIndex((i+1)%N, 0);
			tubeStrips[n++] = vertexIndex(i, 0);
		}
	}

	private double[] normalize(final double[] vector) {
		final double magnitude = Math.sqrt(vector[0]*vector[0] + vector[1]*vector[1] + vector[2]*vector[2]);
		final double[] normal = new double[3];
		normal[0] = vector[0]/magnitude;
		normal[1] = vector[1]/magnitude;
		normal[2] = vector[2]/magnitude;
		return normal;
	}

	

	/* (non-Javadoc)
	 * @see javax.media.opengl.GLEventListener#reshape(javax.media.opengl.GLAutoDrawable, int, int, int, int)
	 */
	@Override
	public void reshape(final GLAutoDrawable glDrawable, final int x, final int y, final int width, final int height) {
		final GL2 gl = glDrawable.getGL().getGL2();
		// set viewport
		gl.glViewport(0, 0, width, height);
		// set perspective
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		final float widthHeightRatio = (float) width / (float) height;
		glu.gluPerspective(45.0f, widthHeightRatio, 1.0f, 1.5*maxPosition);
		display(glDrawable);
	}


	private void run() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		final GLProfile prof = GLProfile.get(GLProfile.GL2);
		final GLCapabilities glcaps = new GLCapabilities(prof);
		glcaps.setDoubleBuffered(true);
		glcaps.setHardwareAccelerated(true);
		glcaps.setDepthBits(16);
		final GLCanvas glcanvas = new GLCanvas(glcaps);
		glcanvas.addGLEventListener(this);
		glcanvas.setSize(width, height);
		glcanvas.addKeyListener(new KeyListener(){
			@Override
			public void keyPressed(final KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ESCAPE){
					System.exit(0);
				}
				if(e.getKeyCode() == KeyEvent.VK_Z){
					cameraPosition[2] += ZOOM_DELTA;
					if(cameraPosition[2] > maxPosition){
						cameraPosition[2] = maxPosition;
					}
				} else if(e.getKeyCode() == KeyEvent.VK_X){
					cameraPosition[2] -= ZOOM_DELTA;
					if(cameraPosition[2] < 0.0f){
						cameraPosition[2] = 0.0f;
					}
				}
				if(e.getKeyCode() == KeyEvent.VK_W){
					cameraPosition[1] += ZOOM_DELTA;
					if(cameraPosition[1] > maxPosition/2){
						cameraPosition[1] = maxPosition/2;
					}
				} else if(e.getKeyCode() == KeyEvent.VK_S){
					cameraPosition[1] -= ZOOM_DELTA;
					if(cameraPosition[1] < -maxPosition/2){
						cameraPosition[1] = -maxPosition/2;
					}
				}
				if(e.getKeyCode() == KeyEvent.VK_A){
					cameraPosition[0] -= ZOOM_DELTA;
					if(cameraPosition[0] < -maxPosition/2){
						cameraPosition[0] = -maxPosition/2;
					}
				} else if(e.getKeyCode() == KeyEvent.VK_D){
					cameraPosition[0] += ZOOM_DELTA;
					if(cameraPosition[0] > maxPosition/2){
						cameraPosition[0] = maxPosition/2;
					}
				}
				if(e.getKeyCode() == KeyEvent.VK_1){
					drawType = 0;
				}
				if(e.getKeyCode() == KeyEvent.VK_2){
					drawType = 1;
				}
				if(e.getKeyCode() == KeyEvent.VK_3){
					drawType = 2;
				}
                                if(e.getKeyCode() == KeyEvent.VK_UP){
                                    FORWARD = FORWARD + .25f;
                                }
                                if(e.getKeyCode() == KeyEvent.VK_DOWN){
                                    FORWARD = FORWARD - .25f;
                                }
                                if(e.getKeyCode() == KeyEvent.VK_LEFT){
                                    RIGHT = RIGHT - .25f;
                                }
                                if(e.getKeyCode() == KeyEvent.VK_RIGHT){
                                    RIGHT = RIGHT + .25f;
                                }
			}
			@Override
			public void keyReleased(final KeyEvent e) {
			}
			@Override
			public void keyTyped(final KeyEvent e) {
			}
		});
		MouseInputAdapter mia = new MouseInputAdapter(){

			@Override
			public void mouseDragged(MouseEvent e) {
				dx = e.getX()-mousex;
				dy = e.getY()-mousey;
				rotatex += dx*DEGREES_PER_PIXEL;
				rotatey += dy*DEGREES_PER_PIXEL;
				if (rotatex > 360){
					rotatex = 360;
				}
				if(rotatex < -360){
					rotatex = -360;
				}
				if(rotatey > 360){
					rotatey = 360;
				}
				if(rotatey < -360){
					rotatey = -360;
				}
				dx = 0;
				dy = 0;
				mousex = e.getX();
				mousey = e.getY();
			}

			@Override
			public void mousePressed(MouseEvent e) {
				mousex = e.getX();
				mousey = e.getY();
			}



		};
		glcanvas.addMouseListener(mia);
		glcanvas.addMouseMotionListener(mia);
		this.getContentPane().add(glcanvas, BorderLayout.CENTER);
		setSize(800, 800);
		centerWindow(this);
		animator = new FPSAnimator(30);
		animator.add(glcanvas);
		setVisible(true);
		glcanvas.requestFocus();
		animator.start();
	}

	/**
	 * @param gl
	 */

	private int vertexIndex(final int i, final int j){
		return i*M+j;
	}



	private double x(final double t) {
		return (A + B*Math.cos(Q*t)) * Math.cos(P*t);
	}


	private double y(final double t) {
		return (A + B*Math.cos(Q*t)) * Math.sin(P*t);
	}


	private double z(final double t) {
		return B*Math.sin(Q*t);
	}


}



