/********************************************************************
    Copyright (c) 2000-2008 Steven E. Hugg.

    This file is part of Exoflight.

    Exoflight is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Exoflight is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Exoflight.  If not, see <http://www.gnu.org/licenses/>.
*********************************************************************/
package com.fasterlight.exo.main;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Date;

import javax.media.opengl.*;

import com.fasterlight.exo.game.*;
import com.fasterlight.exo.newgui.*;
import com.fasterlight.exo.orbit.*;
import com.fasterlight.exo.orbit.traj.LandedTrajectory;
import com.fasterlight.exo.ship.SpaceShip;
import com.fasterlight.exo.ship.sys.ShipAttitudeSystem;
import com.fasterlight.exo.sound.GameSound;
import com.fasterlight.exo.strategy.*;
import com.fasterlight.game.*;
import com.fasterlight.glout.*;
import com.fasterlight.io.IOUtil;
import com.fasterlight.vecmath.Vector3f;

/**
  * The EXOFLIGHT main program.
  */
public class Exoflight implements Runnable, Constants, NotifyingEventObserver
{
	MenuedFrame mainframe;
	Container mainwindow;

	MainAWTComponent awtadapter;
	MainGUIContext guictx;

	Universe u;
	SpaceGame game;
	Agency agency;

	GLOPageStack topstack;

	long starttime = -1;

	JoystickManager joy;

	//   SDLSurface sdl_surf;

	GameSound gsound;

	// mission category/name
	String defaultMissionCategory = "Free Flight";
	String defaultMissionName = "Lunar Hop";

	Engine engine;

	static final int WORLD_WIDTH = 1024;
	static final int WORLD_HEIGHT = 768;

	static int SCRN_WIDTH =
		Integer.parseInt(System.getProperty("exo.scrnwidth", "1024"));
	static int SCRN_HEIGHT =
		Integer.parseInt(System.getProperty("exo.scrnheight", "768"));

	static final String FRAME_ICON_PATH =
		"com/fasterlight/exo/main/exoicon1-16.png";
	static final String FRAME_TITLE = "EXOFLIGHT";

	static boolean FULL_SCREEN =
		"true".equals(System.getProperty("exo.fullscreen", "false"));
	static boolean USE_NATIVE_MENU =
		System.getProperty("os.name").startsWith("Mac OS");
	static boolean ADD_LISTENERS = true;
//		System.getProperty("os.name").startsWith("Linux");

	//

	long t1 = System.currentTimeMillis();
	long t2;
	private GLCanvas glcanvas;

	/**
	 * This thread just pops events off of the EventQueue, and sends them
	 * to the outer Exoflight class.
	 */
	class Engine extends Thread
	{
		boolean running = true;
		Engine()
		{
			super("Game Event Dispatcher");
		}
		public void run()
		{
			while (running)
			{
				try
				{
					EventQueue.invokeAndWait(Exoflight.this);
					Thread.yield();
				} catch (Throwable ee)
				{
					ee.printStackTrace(System.out);
				}
			}
		}
	}

	public void run()
	{
		try
		{
			if (mainwindow.isShowing()
				&& mainframe.getState() != Frame.ICONIFIED)
			{
				glcanvas.display();
			}
			if (game == null)
				return;

			updateControls();
			game.getGovernor().update();
			if (getTracked() instanceof SpaceShip)
				gsound.update((SpaceShip) getTracked());
		} catch (Throwable exc)
		{
			exc.printStackTrace(System.out);
		}
	}

	void updateControls()
	{
		if (guictx == null)
			return;
		if (joy == null || joy.getNumSticks() == 0)
			return;
		{
			SpaceShip ship = guictx.getCurrentShip();
			if (ship == null)
				return;
			if (ship.getShipAttitudeSystem().getRCSManual()
				!= ShipAttitudeSystem.RCS_MANINHIBIT)
			{
				joy.updateDevices();
				float dz = 0.25f;
				float x = joy.getAxis(JoystickManager.X_AXIS);
				float y = joy.getAxis(JoystickManager.Y_AXIS);
				float z = joy.getAxis(JoystickManager.Z_AXIS);
				if (Math.abs(x) < dz)
					x = 0;
				else
					x = AstroUtil.sign(x) * (Math.abs(x) - dz) / (1 - dz);
				if (Math.abs(y) < dz)
					y = 0;
				else
					y = AstroUtil.sign(y) * (Math.abs(y) - dz) / (1 - dz);
				if (Math.abs(z) < dz)
					z = 0;
				else
					z = AstroUtil.sign(z) * (Math.abs(z) - dz) / (1 - dz);
				Vector3f v = new Vector3f(-y, -x, z);
				ship.getShipAttitudeSystem().setRotationController(v);

				// do throttle
				if (ship.getShipAttitudeSystem().getThrottleManual()
					== ShipAttitudeSystem.THROT_MANUAL)
				{
					float t = 0.5f - joy.getAxis(JoystickManager.THROT_AXIS);
					if (t < 0.01f)
						t = 0;
					ship.getShipAttitudeSystem().setManualThrottle(t);
				}
			}
		}
	}

	public boolean notifyObservedEvent(NotifyingEvent event)
	{
		// if we get an alert, know the code& play sound
		if (event instanceof AlertEvent)
		{
			String code = ((AlertEvent) event).getCode();
			if (code != null)
			{
				gsound.alertCode(code);
			}
		}
		if (event.getMessage() == null)
			return false;
		return game.getGovernor().decreaseTimeScaleTo(1.0f);
	}

	//

	void reloadUI()
	{
		// make top page stack component
		guictx.removeAllChildren();
		guictx.reset();

		// set current ship
		java.util.List ships = game.getAgency().getShips();
		if (ships.size() > 0)
		{
			guictx.setProp("ship", ships.get(0));
		}

		game.setValue("gsound", gsound);

		GUILoader loader = new GUILoader(null, game, guictx);

		try
		{
			topstack = (GLOPageStack) loader.load("panels/pages.txt");
			System.out.println("loading topstack @ " + awtadapter.getSize());
			guictx.add(topstack);
			guictx.setSize(topstack.getSize());
			guictx.resize(awtadapter.getWidth(), awtadapter.getHeight());

			loader.setTopComponent(guictx);
			// todo: WHY does this thing have to have a parent to layout?
			if (USE_NATIVE_MENU)
			{
				GLOMenu gloMenu = new GLOMenu();
				gloMenu.load("panels/main.mnu");
				mainframe.setGLOMenu(gloMenu);
			} else
			{
				GLOComponent menu = loader.load("panels/menubar.txt");
			}
			GLOComponent misc = loader.load("panels/misc.txt");
		} catch (IOException ioe)
		{
			ioe.printStackTrace(System.out);
		}

		guictx.reloadCommands();
		mainframe.setCmdmgr(guictx.getCommandManager());
	}

	class MainAWTComponent extends GLOAWTComponent
	{
		public MainAWTComponent(int w, int h)
		{
			super(w, h);
		}
		protected GLOContext makeContext()
		{
			GLOContext ctx = new MainGUIContext(game);
			return ctx;
		}
		protected void makeComponents()
		{
			super.makeComponents();
			guictx = (MainGUIContext) getContext();
			guictx.setMinimumSize(WORLD_WIDTH, WORLD_HEIGHT);
			guictx.setSize(WORLD_WIDTH, WORLD_HEIGHT);
			guictx.setViewSize(SCRN_WIDTH, SCRN_HEIGHT);
		}
		public void init(GLAutoDrawable drawable)
		{
			super.init(drawable);

			// show title
			loadTitleScreen();
			//sDisplay();
		}
	}

	synchronized void startEngine()
	{
		if (engine == null)
		{
			engine = new Engine();
			engine.start();
			engine.setPriority(Thread.NORM_PRIORITY - 1); //todo: const
		}
	}

	//

	class MainGUIContext extends GUIContext
	{

		public MainGUIContext(SpaceGame game)
		{
			super(game);
			resizeViewSize = false;
			cmdmgr = new GLOCommandManager(this);
		}
		public void setGame(SpaceGame game)
		{
			super.setGame(game);
		}
		void reloadCommands()
		{
			try
			{
				cmdmgr.loadCommands("commands/commands.txt");
				cmdmgr.loadControlBindings("commands/keys.txt");
			} catch (IOException ioe)
			{
				ioe.printStackTrace(System.out);
			}
		}
		
		boolean numLockWarning;
		
		private GLOKeyEvent fixupKeyEvent(GLOKeyEvent e)
		{
			// convert keypad keys to arrow keys
			int code = e.getKeyCode();
			switch (code)
			{
				case KeyEvent.VK_NUMPAD8:
				case KeyEvent.VK_NUMPAD2:
				case KeyEvent.VK_NUMPAD4:
				case KeyEvent.VK_NUMPAD6:
					if (!numLockWarning)
					{
						numLockWarning = true;
						throw new GLOUserException("Please turn NUM LOCK off.");
					}
					break;
				case 224:
				//case KeyEvent.VK_NUMPAD8:
					code = KeyEvent.VK_UP;
					break;
				case 225:
				//case KeyEvent.VK_NUMPAD2:
					code = KeyEvent.VK_DOWN;
					break;
				case 226:
				//case KeyEvent.VK_NUMPAD4:
					code = KeyEvent.VK_LEFT;
					break;
				case 227:
				//case KeyEvent.VK_NUMPAD6:
					code = KeyEvent.VK_RIGHT;
					break;
			}
			if (code != e.getKeyCode())
				return new GLOKeyEvent(guictx, e.getFlags(), code, e.getChar(), e.isPressed());
			else
				return e;
		}
		private boolean keyPressed(GLOKeyEvent e)
		{
			if (e.getFlags() != 0)
				return false;
			switch (e.getKeyCode())
			{
				case KeyEvent.VK_F10 :
					try
					{
						gsound.reset();
					} catch (Exception ex)
					{
						ex.printStackTrace(System.out);
						throw new RuntimeException(ex.toString());
					}
					return true;
				case KeyEvent.VK_F11 :
					guictx.clearCaches();
					return true;
				case KeyEvent.VK_F12 :
					game.setDebug(!game.getDebug());
					return true;
				case KeyEvent.VK_O :
					Conic o =
						UniverseUtil.getGeocentricConicFor(getCurrentShip());
					if (o == null)
						return false;
					System.out.println(o.getElements());
					System.out.println(
						AstroUtil.gameTickToJavaDate(
							AstroUtil.dbl2tick(o.getInitialTime())));
					return true;
				case KeyEvent.VK_T :
					if (getSelected() != null && getCurrentShip() != null)
					{
						getCurrentShip().getShipTargetingSystem().setTarget(
							getSelected());
						// todo: message
						if (getTracked() != null)
							setSelected(getTracked());
					}
					return true;
				case KeyEvent.VK_R :
					if (getSelected() instanceof SpaceShip)
					{
						setCurrentShip((SpaceShip) getSelected());
					} else
					{
						setTracked(getSelected());
					}
					// todo: message
					return true;
				case KeyEvent.VK_1 :
				case KeyEvent.VK_2 :
				case KeyEvent.VK_3 :
				case KeyEvent.VK_4 :
				case KeyEvent.VK_5 :
				case KeyEvent.VK_6 :
				case KeyEvent.VK_7 :
				case KeyEvent.VK_8 :
				case KeyEvent.VK_9 :
					{
						java.util.List ships = agency.getShips();
						int i = e.getKeyCode() - KeyEvent.VK_1;
						if (i < ships.size())
						{
							SpaceShip ship = (SpaceShip) ships.get(i);
							setSelected(ship);
							setCurrentShip(ship);
						}
						return true;
					}
				case KeyEvent.VK_0 :
					if (getCurrentShip() != null
						&& getCurrentShip().getShipTargetingSystem().getTarget()
							!= null)
					{
						setSelected(
							getCurrentShip()
								.getShipTargetingSystem()
								.getTarget());
					}
					return true;
				case KeyEvent.VK_H :
					if (getSelected() instanceof SpaceShip)
					{
						Trajectory traj = getSelected().getTrajectory();
						if (traj instanceof LandedTrajectory)
							 ((LandedTrajectory) traj).setLockedDown(false);
					}
					break;
				case KeyEvent.VK_F9 :
					Vehicle.reloadVehicles();
					guictx.loadDefaultShaders();
					reloadUI();
					SettingsGroup.updateAll();
					break;
			}
			return false;
		}

		public boolean handleEvent(GLOEvent event)
		{
			if (event instanceof GLOKeyEvent)
			{
				GLOKeyEvent ke = fixupKeyEvent((GLOKeyEvent)event);
				boolean exec = cmdmgr.executeControl(ke);
				if (exec)
					return true;
				if (ke.isPressed())
				{
					if (keyPressed(ke))
						return true;
					else
						System.out.println(ke);
				}
			} else if (event instanceof GLOActionEvent)
			{
				Object act = ((GLOActionEvent) event).getAction();
				if (act instanceof SpaceGame)
				{
					System.out.println("Resetting to game " + act);
					resetGame((SpaceGame) act);
					return true;
				}
				if (act instanceof String) // todo? Command object?
				{
					String s = act.toString();
					if (s.equals("Game:Exit"))
					{
						exitProgram();
						return true;
					} else if (s.equals("Game:Reset"))
					{
						if (game != null && game.getCurrentMission() != null)
						{
							resetGame(
								guictx.createNewGame(game.getCurrentMission()));
						} else
							throw new GLOUserException("No mission is currently active.");
						return true;
					} else if (cmdmgr != null)
					{
						if (cmdmgr.execute(s))
							return true;
					}
				}
			}
			return super.handleEvent(event);
		}
	}

	void exitProgram()
	{
		game = null;
		if (mainframe != null)
			mainframe.setVisible(false);
		if (mainwindow != null)
			mainwindow.setVisible(false);
		try
		{
			engine.running = false;
			engine.join(1000);
		} catch (Exception exc)
		{
			exc.printStackTrace();
		}
		try
		{
			if (joy != null)
				joy.close();
			if (gsound != null)
				gsound.close();
			Thread.sleep(500);
		} catch (Exception exc)
		{
			exc.printStackTrace();
		}
		System.exit(0); //todo??
	}

	void resetGame(SpaceGame newgame)
	{
		if (game != null)
		{
			game.removeObserver(this);
		}

		// show title
		loadTitleScreen();
		//awtadapter.sDisplay();

		game = newgame;
		game.addObserver(this);
		u = game.getUniverse();
		agency = game.getAgency();
		if (guictx != null)
		{
			guictx.setGame(game);
			this.reloadUI();
			try
			{
				gsound.reset();
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	void startDefaultGame()
	{
		Mission m = Mission.getMission(defaultMissionCategory,
				defaultMissionName);
		// resetGame(guictx.createNewGame(m));
		// TODO: we can't add the GUI here because we don't yet have a GUI :P
		SpaceGame newgame = new SpaceGame();
		m.prepare(newgame);
		m.getSequencer().setVar("ui", guictx);
		m.getSequencer().start();
		resetGame(newgame);
	}

	Image getImage(String path)
	{
		try
		{
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			byte[] imgarr = IOUtil.readBytes(IOUtil.getBinaryResource(path));
			Image image = toolkit.createImage(imgarr);
			return image;
		} catch (Exception ioe)
		{
			ioe.printStackTrace();
			return null;
		}
	}

	Image getIconImage()
	{
		return getImage(FRAME_ICON_PATH);
	}

	void showMainWindow()
	{
		mainframe = new MenuedFrame(FRAME_TITLE);
		//GraphicsDevice scrnDevice = null;
		if (FULL_SCREEN)
		{
			mainframe.setUndecorated(true);
			mainwindow = mainframe.getContentPane();
			//			mainwindow = new Window(mainframe);
			//			scrnDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
			//			System.out.println("setting full screen: " + scrnDevice.getDisplayMode());
		} else
			mainwindow = mainframe.getContentPane();

		Image iconimage = getIconImage();
		if (iconimage != null)
			mainframe.setIconImage(iconimage);
		else
			System.err.println("could not find icon image");

		// so that Java MIGHT quite when we close the manu
		mainframe.addWindowListener(new WindowAdapter()
		{
			public void windowClosed(WindowEvent e)
			{
				// again, gl4java thwarts us
				//guictx.loadDialog("panels/dialogs/exit.txt");
				exitProgram();
			}
			public void windowClosing(WindowEvent e)
			{
				windowClosed(e);
			}
		});

		//Validate frames that have preset sizes
		//Pack frames that have useful preferred size info, e.g. from their layout
		System.out.println("showing mainframe");

		awtadapter = new MainAWTComponent(SCRN_WIDTH, SCRN_HEIGHT);
		GLCapabilities glcaps = new GLCapabilities();
		glcaps.setHardwareAccelerated(true);
		glcaps.setDepthBits(32);
		glcanvas = awtadapter.createGLCanvas();
		mainwindow.setLayout(new BorderLayout());
		mainwindow.add(glcanvas, BorderLayout.CENTER);

		mainframe.pack();
		if (FULL_SCREEN)
		{
			Rectangle desktopRect =
				GraphicsEnvironment
					.getLocalGraphicsEnvironment()
					.getMaximumWindowBounds();
			System.out.println("desktop = " + desktopRect);
			mainframe.setBounds(desktopRect);
		}

		mainframe.setVisible(true);
		if (ADD_LISTENERS)
		{
			awtadapter.addListeners(glcanvas);
			System.out.println("Added listeners");
		}
		glcanvas.requestFocusInWindow();
	}

	void loadTitleScreen()
	{
		GUILoader loader = new GUILoader(null, game, guictx);
		loader.setTopComponent(guictx);
		try
		{
			loader.load("panels/loading_screen.txt");
		} catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
	}

	void start()
	{
		showMainWindow();

		// start stick
		initializeJoystick();

		gsound = GameSound.getGameSound();
		System.out.println("sound opened = " + gsound.isOpened());

		// load default game
		startDefaultGame();

		// start event queue going
		startEngine();
	}

	private void initializeJoystick()
	{
		try
		{
			joy = new JoystickManager();
			joy.open();
			System.out.println("Found " + joy.getNumSticks() + " joysticks");
		} catch (Throwable e)
		{
			System.out.println("Could not initialize joystick: " + e);
			joy = null;
		}
	}

	//Construct the application
	public Exoflight() throws Exception
	{
	}

	///

	public UniverseThing getSelected()
	{
		return (UniverseThing) guictx.getProp("selected");
	}

	public void setSelected(UniverseThing selected)
	{
		guictx.setProp("selected", selected);
	}

	public UniverseThing getTracked()
	{
		return (UniverseThing) guictx.getProp("tracked");
	}

	public void setTracked(UniverseThing tracked)
	{
		guictx.setProp("tracked", tracked);
	}

	///

	public static void main(String[] args) throws Exception
	{
		try
		{
			// use settings.ini, create one & don't populate if its not there
			Settings.setFilename("settings.ini");
			//Settings.setWriteDefaults(true);

			// redirect stdout, stderr
			PrintStream out = null;
			String outfile = null;
			for (int i = 0; i < args.length; i++)
			{
				if (args[i].equals("-stdout"))
				{
					String fn = args[++i];
					if (!fn.equals(outfile))
					{
						outfile = fn;
						out = new PrintStream(new FileOutputStream(fn));
					}
					System.setOut(out);
				} else if (args[i].equals("-stderr"))
				{
					String fn = args[++i];
					if (!fn.equals(outfile))
					{
						outfile = fn;
						out = new PrintStream(new FileOutputStream(fn));
					}
					System.setErr(out);
				} else if (args[i].equals("-download"))
				{
					new Downloader().checkAll();
				} else
					System.err.println("Unrecognized argument: " + args[i]);
			}

			Exoflight mainprog = new Exoflight();
			mainprog.start();
		} catch (Throwable e)
		{
			e.printStackTrace();
		}
	}
}
