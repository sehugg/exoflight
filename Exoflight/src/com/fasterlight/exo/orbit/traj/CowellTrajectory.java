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
package com.fasterlight.exo.orbit.traj;

import java.util.Iterator;

import com.fasterlight.exo.game.AlertEvent;
import com.fasterlight.exo.orbit.*;
import com.fasterlight.exo.orbit.integ.*;
import com.fasterlight.exo.ship.SpaceShip;
import com.fasterlight.game.*;
import com.fasterlight.util.Vec3d;
import com.fasterlight.vecmath.*;

/**
  * A trajectory that integrates the position of the object
  * using the Runge-Kutta-Fehlberg 4/5 order method (large timesteps)
  * and the Runge Kutta 4th order method (small timesteps).
  * The attraction of the parent body on the object is intergrated,
  * and other forces may be added with the addPerturbation() method.
  *
  * todo: enforce idempotent get methods!!!
  * todo: oldy0 can be 'null' or invalid before handleEvent() is called
  * todo: why does switching from OrbitTrajectory change attitude?
  * make sure to do state diagram
  */
public class CowellTrajectory extends DefaultMutableTrajectory implements Derivatives
{
	double U;
	Perturbation bodyperturb;

	PosUpdateEvent posevent;
	int timestep = LARGE_TIMESTEP;
	HeunEuler heul = new HeunEuler();
	RungeKutta4 rk4 = new RungeKutta4();
	RungeKuttaFehlberg78 rkf78 = new RungeKuttaFehlberg78();
	RungeKuttaFehlberg45 rkf45 = new RungeKuttaFehlberg45();

	// laststate is the last state solved by solve()
	// but if integrateAngular is false, only r & v are valid
	RKState4 laststate = new RKState4();
	long lasttime;
	double lastIntegrationError;

	RKState4 oldy0;
	RKState4 olddy1; // y0 and dy1 at t0

	// whether or not to integrate orientation
	private boolean integrateAngular = true;

	PerturbForce lastpforce;
	PerturbForce curpforce; // curpforce is the one we want to show the world

	private boolean in_routine; // set when we are computing forces

	private byte errortries = 0;
	static final int NUM_ERROR_TRIES = 3; // # of iters before we can increase the rk step

	private byte crashtries = 0;
	static final int NUM_CRASH_TRIES = 10; // # of iters before we crash

	private double gcheck_rad; // alt at which ground gets checked
	private double atmo_ceiling_rad2;

	private SpaceShip ship;

	private int wheels_mask; // which wheels (ContactPoints) are touching
	private int wheels_detected; // wheels mask, but computed during f()
	private double min_wheelvel2_detected;

	static final boolean alwaysCheckContacts = true;
	static final boolean crashExpendable = true;

	static final int STATE_LENGTH_POS = 6;
	static final int STATE_LENGTH_POS_ANG = STATE_LENGTH_POS + 7;

	//

	public CowellTrajectory()
	{
	}
	public CowellTrajectory(UniverseThing ref, Vector3d r0, Vector3d v0, long t0, Orientation ort)
	{
		set(ref, r0, v0, t0, ort);
		/*
		integ.setMinimumStepSize(1d/Constants.TICKS_PER_SEC);
		integ.setAccuracy(1e-9);
		integ.setVerbose();
		*/
	}
	public void set(UniverseThing ref, Vector3d r0, Vector3d v0, long t0, Orientation ort)
	{
		boolean act = isActive();
		if (act)
			thing.setTrajectory(null);

		lasttime = INVALID_TICK;
		super.set(ref, r0, v0, t0, ort);
		this.U = ref.getMass() * GRAV_CONST_KM;
		if (U != 0)
		{
			bodyperturb = new ParentBodyPerturbation();
		}
		else
			bodyperturb = null;
		if (ref instanceof Planet)
		{
			Planet planet = (Planet) ref;
			gcheck_rad = planet.isGaseous() ? 0 : planet.getMaxRadius();
			atmo_ceiling_rad2 =
				(planet.getAtmosphere() != null)
					? AstroUtil.sqr(planet.getAtmosphere().getCeiling() + planet.getRadius())
					: 0;
		}
		else
			gcheck_rad = ref.getRadius();

		if (act)
			thing.setTrajectory(this);
	}

	public boolean activateTrajectory()
	{
		if (!super.activateTrajectory())
		{
			if (thing instanceof SpaceShip)
				ship = (SpaceShip) thing;
			this.t0 = getGame().time();
			posevent = new PosUpdateEvent(t0);
			getGame().postEvent(posevent);
			return false;
		}
		else
			return true;
	}

	public void deactivate()
	{
		updateInitialValues();
		if (posevent != null)
		{
			getGame().cancelEvent(posevent);
			posevent = null;
		}
		ship = null;
		super.deactivate();
	}

	//

	// assumes ort == ort0
	void notifyShipForce(PerturbForce pf1, RKState4 y0)
	{
		double rad = ship.getRadius();
		double mass = ship.getMass();

		PerturbForce pf2 = new PerturbForce(pf1);
		// disregard 'a' component (gravity)
		pf2.a.set(0, 0, 0);
		// add force to accel, because 'accel' is the
		// only thing ship.notifyForce() looks at
		pf2.a.scaleAdd(1d / mass, pf2.f, pf2.a);
		pf2.a.scaleAdd(rad, y0.d, pf2.a);
		// transform to structure coords
		ort0.invTransform(pf2.a);
		// notify ship of impending force (or doom)
		ship.notifyForce(pf2.a);
	}

	//

	class PosUpdateEvent extends GameEvent
	{
		RKState4 y0, dy1; // y0 and dy1 at eventtime

		PosUpdateEvent(long time)
		{
			super(time);
		}
		private boolean checkForGroundInteraction(RKState4 y0, double rl, double trad, long time)
		{
			boolean result = false;
			if (ref instanceof Planet && ship != null)
			{
				ContactPoint[] cpts = ship.getContactPoints();
				if (cpts == null || cpts.length == 0)
					return false;
				Planet planet = (Planet) ref;
				double pelev =
					planet.getRadius()
						+ planet.getElevationAt(y0.a, time * (1d / Constants.TICKS_PER_SEC));
				// add a little slop (todo: use slope of ground)
				pelev += 0.002;
				double altagl = rl - trad - pelev;
				if (altagl < 0)
				{
					Orientation ort = y0.c;
					// iterate thru each contact point
					Vector3d r = new Vec3d();
					for (int i = 0; i < cpts.length; i++)
					{
						// if this isn't already contacted, checkit
						boolean contacted = (wheels_mask & (1 << i)) != 0;
						if (!contacted)
						{
							Vector3f stpt = cpts[i].extpos;
							r.set(stpt);
							ort.transform(r);
							r.add(y0.a);

							rl = r.length();
							if (rl < pelev)
							{
								if (debug3)
								{
									System.out.println(
										"CONTACT: "
											+ getThing()
											+ ", agl="
											+ (rl - pelev)
											+ ", t="
											+ time);
									System.out.println("\tpt = " + stpt);
								}
								wheels_mask |= (1 << i);
								result = true;
								if (debug3)
									System.out.println(thing + " added wheel " + i);
							}
						}
					}
				}
			}
			return result;
		}
		public void handleEvent(Game game)
		{
			if (y0 == null)
			{
				y0 = new RKState4(r0, v0, ort0, angvel);
				oldy0 = new RKState4(y0);
				olddy1 = new RKState4(y0);
			}
			else
			{
				r0.set(y0.a);
				v0.set(y0.b);
				if (integrateAngular)
				{
					ort0.set(y0.c);
					angvel.set(y0.d);
					angt0 = eventtime;
				}
			}

			if (checkPerturbs())
				return;

			if (checkInfluenceExit())
				return;

			// integrate angular perturbations if we have drag,
			// or if we have user perturbations
			integrateAngular = false;
			if (dragperturb != null)
			{
				double y0rl2 = y0.a.lengthSquared();
				integrateAngular = (y0rl2 < atmo_ceiling_rad2);
			}
			if (!integrateAngular && countUserPerturbations() > 0)
				integrateAngular = true;

			// compute forces for t0 = now
			t0 = eventtime;
			dy1 = f(t0, y0, true);
			olddy1.set(dy1);
			curpforce = lastpforce;

			if (checkInfluenceEnter())
				return;

			oldy0.set(y0);

			// lower timestep if too much torque
			//			timestep = Math.min(timestep, 1<<((int)(8/(0.001+lastpforce.m.lengthSquared()))));

			boolean inc_rk_step = false;

			// curts is the actual time step calculated in this step
			// while 'timestep' is the time step used for the next iteration
			int curts = timestep;

			// iterate until we decide on a timestep for this frame
			do
			{
				wheels_detected = 0;
				min_wheelvel2_detected = 1e30;

				// update y0 and dy1 for next tick
				// TODO: wasting first integration step (dy1)
				double error = integrateStep(curts, y0, dy1, true);
				lastIntegrationError = error;

				if (debug)
					System.out.println(
						"t="
							+ game.time()
							+ " timestep="
							+ timestep
							+ " error="
							+ error
							+ " "
							+ integrateAngular);

				if (ship != null && ship.isExpendable())
					error *= COARSE_ERROR_FACTOR;

				// check for ground interaction
				if (timestep == MIN_TIMESTEP || error <= HI_ERROR_THRESH)
				{
					double rl = y0.a.length();
					double thingrad = thing.getRadius();
					if (rl - thingrad < gcheck_rad)
					{
						// if any wheels are newly detected, this fn returns true
						// so we set error to a high value and go through the
						// loop again, to pick up the interaction forces
						if (checkForGroundInteraction(y0, rl, thingrad, t0 + curts))
						{
							ship.getShipWarningSystem().setWarning("CONTACT", true);
							y0.set(oldy0);
							continue;
						}
						else
						{
							// otherwise, clear the wheel mask only if we don't detect
							// any wheels at all
							if (wheels_detected == 0)
							{
								wheels_mask = 0;
								ship.getShipWarningSystem().setWarning("CONTACT", false);
							}
						}
						if (debug3 && wheels_mask+wheels_detected != 0)
							System.out.println(
								thing
									+ " mask is now "
									+ Integer.toString(wheels_mask, 16)
									+ ", detected "
									+ Integer.toString(wheels_detected, 16));
					}
					else
						wheels_mask = 0;

					// crash if our velocity < minimum
					if (wheels_mask != 0 && min_wheelvel2_detected < MIN_SURFACE_VEL_2)
					{
						if (++crashtries > NUM_CRASH_TRIES)
						{
							crash();
							return;
						}
						if (debug2)
							System.out.println(
								"# of crash tries = " + crashtries + ", " + min_wheelvel2_detected);
					}
					else
						crashtries = 0;
				}

				if (!(error < HI_ERROR_THRESH))
				{
					// if we've gone below the minimum timestep,
					// correct the velocity and continue
					// TODO: this still doesn't work too well
					if (curts / 2 < MIN_TIMESTEP)
					{
						curts = MIN_TIMESTEP;
						// this is the maximum error we'll tolerate
						// if we exceed it, we just crash() which
						// sets the trajectory to LandedTrajectory
						// (stops it in its tracks)
						if (!(error < BAD_ERROR_THRESH))
						{
							if (DO_UCE_WARNING && ship != null)
								ship.getShipWarningSystem().setWarning(
									"UCE",
									"Trajectory overflow");
							System.out.println(ship + " error = " + error);
							crash();
							return;
						}
						// otherwise, just deal with the error and continue
						break;
					}
					else
					{
						// when we go below INITAL_TIMESTEP, our first
						// stop is SMALL_TIMESTEP so we can have powers of 2
						// all the way down to MIN_TIMESTEP
						if (timestep >= LARGE_TIMESTEP)
						{
							timestep /= 2;
							if (timestep < LARGE_TIMESTEP)
							{
								timestep = SMALL_TIMESTEP;
							}
						} else {
							timestep /= 2;
						}
						curts = timestep;
					}
					errortries = 0;
					y0.set(oldy0);
				}
				else
				{
					if (error < LO_ERROR_THRESH)
						inc_rk_step = true;
					break;
				}
			}
			while (true);
			// notify ship, if it is a ship
			curpforce = lastpforce;
			if (debug2)
				System.out.println("curpforce = " + curpforce);
			if (ship != null)
			{
				notifyShipForce(curpforce, y0);
				ship.getStructure().addHeat(0);
			}

			// next event is in 'tdelta' ticks
			long t = eventtime + curts;
			eventtime = t;
			lasttime = INVALID_TICK;
			game.postEvent(this);

			// now increase the rk step, if necc.
			if (inc_rk_step)
			{
				if (errortries++ > NUM_ERROR_TRIES && timestep < MAX_TIMESTEP)
				{
					// the INITIAL_TIMESTEP is a multiple of the smallest possible
					// timestep that can have only integer time values in the
					// intermediate steps .. so we want to try to keep values
					// a multiple of that if possible
					if (timestep < LARGE_TIMESTEP)
					{
						timestep *= 2;
						if (timestep > LARGE_TIMESTEP)
							timestep = LARGE_TIMESTEP;
					} else {
						timestep *= 2;
					}
				}
			}
		}

		public String toString()
		{
			return "Cowell update: "
				+ getThing()
				+ ", tdelta="
				+ timestep
				+ (integrateAngular ? " ang" : "");
		}
	}

	private double estimateError(double ts, RKState4 y0, RKState4 dy1)
	{
		double error;
		Vector3d r = new Vector3d();
		r.scaleAdd(0.5 * ts * ts, dy1.b, oldy0.a);
		r.scaleAdd(ts, dy1.a, r);
		r.sub(y0.a);
		error = Math.sqrt(r.lengthSquared() / y0.a.lengthSquared());
		// also do velocity
		Vector3d v = new Vector3d();
		v.scaleAdd(ts, dy1.b, oldy0.b);
		v.sub(y0.b);
		double vl = v.length();
		error += vl;

		// angular velocity error
		if (integrateAngular)
		{
			double y0dl2 = y0.d.lengthSquared();
			// limit angular vel
			double angerr =
				Math.sqrt(oldy0.d.lengthSquared() + y0dl2) * ts * HI_ERROR_THRESH;
			error += angerr;
			if (debug2)
				System.out.println("  angerr=" + angerr);
		}

		return error;
	}

	// update r0, v0, ort0, and angvel
	// (or only r0 and v0 if angularVelocity == false)
	private void updateInitialValues()
	{
		if (!isActive())
			return;

		long t = getGame().time();
		solve(t, true);
		curpforce = lastpforce;

		r0.set(laststate.a);
		v0.set(laststate.b);
		if (integrateAngular)
		{
			ort0.set(laststate.c);
			angvel.set(laststate.d);
			angt0 = t;
		}
		t0 = t;
	}

	protected void fixCurrentOrt()
	{
		if (!integrateAngular)
			super.fixCurrentOrt();
	}

	public void refresh()
	{
		fixCurrentOrt();

		// only post new event if there is an event, and if the
		// event will not fire on the current tick
		if (posevent != null && getGame().time() != t0)
		{
			getGame().cancelEvent(posevent);

			updateInitialValues();

			oldy0 = null;
			posevent = new PosUpdateEvent(t0);
			getGame().postEvent(posevent);
		}
	}

	//

	class ParentBodyPerturbation implements Perturbation
	{
		// add force -U*r/(r^3)
		public void addPerturbForce(
			PerturbForce force,
			Vector3d r,
			Vector3d v,
			Orientation ort,
			Vector3d w,
			long time)
		{
			Vector3d acc = new Vec3d(r);
			double r2 = acc.lengthSquared();
			acc.scale(-U / (r2 * Math.sqrt(r2)));
			force.a.add(acc);
		}
	}

	//

	private Orientation tmpQ = new Orientation();
	private Vector3d tmpf = new Vector3d();
	private Vector3d tmpm = new Vector3d();

	// s contains pos, vel
	// returns vel, accel
	private RKState2 f(long time, RKState2 s, boolean taint)
	{
		PerturbForce pf = getAllPerturbForces(s.a, s.b, ort0, angvel, time);
		if (taint)
			lastpforce = pf;

		// compute accel from force & mass
		double mass = thing.getMass(time);
		double invmass = 1d / mass;
		tmpf.scaleAdd(invmass, pf.f, pf.a);

		RKState2 s2 = new RKState2(s.b, tmpf);
		return s2;
	}

	// s contains pos, vel
	// returns vel, accel
	private RKState4 f(long time, RKState4 s, boolean taint)
	{
		PerturbForce pf = getAllPerturbForces(s.a, s.b, s.c, s.d, time);
		if (taint)
			lastpforce = pf;

		// compute accel from force & mass
		double mass = thing.getMass(time);
		double invmass = 1d / mass;
		tmpf.scaleAdd(invmass, pf.f, pf.a);

		// compute moment from torque, mass, inertia
		if (ship != null)
		{
			Vector3d iv = ship.getStructure().getInertiaVector();
			tmpm.set(pf.T);
			s.c.invTransform(tmpm);
			tmpm.x /= mass * iv.x;
			tmpm.y /= mass * iv.y;
			tmpm.z /= mass * iv.z;
			s.c.transform(tmpm);
			if (debug2)
			{
				System.out.println(
					thing.getName() + " m=" + tmpm + " s.c=" + s.c + " mass=" + mass);
				System.out.println("  pf=" + pf);
			}
		}
		else
			tmpm.set(0, 0, 0);

		// do dQ/dt
		tmpQ.set(s.d.x, s.d.y, s.d.z, 0);
		tmpQ.scale(0.5);
		tmpQ.mul(s.c);

		RKState4 s2 = new RKState4(s.b, tmpf, tmpQ, tmpm);
		return s2;
	}

	public double[] derivs(long t, double dt, double[] y)
	{
		// TODO: rounding sucks
		//if (dt*TICKS_PER_SEC != Math.round(dt*TICKS_PER_SEC))
			//throw new RuntimeException("dt=" + (dt*TICKS_PER_SEC));
		switch (y.length)
		{
			case STATE_LENGTH_POS:
			{
				long tick = t + Math.round(dt * TICKS_PER_SEC);
				RKState2 s = new RKState2();
				s.setFrom2(y);
				boolean taint = false; // TODO: taint?
				s = f(tick, s, taint);
				double[] arr = new double[STATE_LENGTH_POS];
				s.copyTo2(arr);
				return arr;
			}
			case STATE_LENGTH_POS_ANG:
			{
				long tick = t + Math.round(dt * TICKS_PER_SEC);
				RKState4 s = new RKState4();
				s.setFrom4(y);
				boolean taint = false; // TODO: taint?
				s = f(tick, s, taint);
				double[] arr = new double[STATE_LENGTH_POS_ANG];
				s.copyTo4(arr);
				return arr;
			}
			default:
				throw new IllegalArgumentException();
		}
	}

	/**
	  * Solves the Runge-Kutta 4th order equation, using either
	  * the 4-component version or 2-component version, depending
	  * on the value of integrateAngular.
	  */
	double integrateStep(int tdelta, RKState4 y0, RKState4 dy1, boolean taint)
	{
		if (tdelta <= MIN_TIMESTEP)
			return integrateRK4(tdelta, y0, dy1, taint);
		else if (integrateAngular)
			return integrateWithAng(tdelta, y0, dy1, taint);
		else
			return integrateWithNoAng(tdelta, y0, dy1, taint);
	}

	// integrate position and orientation
	double integrateWithAng(int tdelta, RKState4 y0, RKState4 dy1, boolean taint)
	{
		double h = tdelta * (1d / TICKS_PER_SEC);
		double[] y = new double[STATE_LENGTH_POS_ANG];
		y0.copyTo4(y);

        double[] yy = new double[STATE_LENGTH_POS_ANG]; // end state
        double[] yerr = new double[STATE_LENGTH_POS_ANG]; // error
        double[] dydx = derivs(t0+tdelta, 0, y);
        rkf45.rkck(y, dydx, t0+tdelta, 0, h, yy, yerr, this);

		y0.setFrom4(yy);
		// TODO: taint force in first step
		this.laststate.set(y0);
        return getMaxArr(yerr);
	}

	// integrate position only
	double integrateWithNoAng(int tdelta, RKState2 y0, RKState2 dy1, boolean taint)
	{
		double h = tdelta * (1d / TICKS_PER_SEC);
		double[] y = new double[STATE_LENGTH_POS]; // start state 
		y0.copyTo2(y);
		
        double[] yy = new double[STATE_LENGTH_POS]; // end state
        double[] yerr = new double[STATE_LENGTH_POS]; // error
        double[] dydx = derivs(t0+tdelta, 0, y);
        rkf45.rkck(y, dydx, t0+tdelta, 0, h, yy, yerr, this);
        
		y0.setFrom2(yy);
		// TODO: taint force in first step
		this.laststate.set(y0);
        return getMaxArr(yerr);
	}
	
	double integrateRK4(int tdelta, RKState4 y0, RKState4 dy1, boolean taint)
	{
		double h = tdelta * (1d / TICKS_PER_SEC);
		double[] y = new double[STATE_LENGTH_POS_ANG];
		y0.copyTo4(y);
		rk4.setStepSize(h);
		double[] yy = rk4.step(t0+tdelta, 0, y, this);
		y0.setFrom4(yy);
		// TODO: taint force in first step
		this.laststate.set(y0);
		// if we are touching the ground, the equations are stiff
		// so we use a very loose error which doesn't do zero crossings
		// estimate error with polynomial d = v*t + 0.5*a*t^2
		return estimateError(tdelta / TICKS_PER_SEC, y0, dy1); // * STIFF_ERROR_SCALE;
	}

	private double getMaxArr(double[] yerr)
	{
		double m = 0;
		for (int i=0; i<yerr.length; i++)
		{
			double err = Math.abs(yerr[i]);
			m = Math.max(err, m);
			// TODO: scale angular components differently
			if (debug && err > HI_ERROR_THRESH)
				System.out.println("element " + i + " went above error thresh: " + yerr[i]);
		}
		return m;
	}
	// solve runge-kutta given r0, v0
	// results in r1, v1
	// return value is dy1
	// "taint", if true, means we can cause sideeffects
	void solve(long time, boolean taint)
	{
		if (in_routine) //recursion
		{
			throw new RuntimeException("Recursive call to CowellTrajectory.solve");
		}
		if (!taint)
		{
			if (time == lasttime) // todo: this is a shitsandwich
			{
				return;
			}
			// if time == t0, we don't need to solve
			// if oldy0 == null, we haven't run an event yet
		}
		lasttime = time;
		if (time == t0 || oldy0 == null)
		{
			laststate.a.set(r0);
			laststate.b.set(v0);
			laststate.c.set(ort0);
			laststate.d.set(angvel);
			return;
		}

		// runge-kutta
		//		RKState4 y0 = new RKState4(r0, v0);
		//		RKState4 dy1 = f(time, y0, taint);
		// TODO: what if outside of boundaries?
		int tdelta = (int)(time - t0);
		integrateStep(tdelta, new RKState4(oldy0), olddy1, taint);
	}

	private Conic getPrivateConic()
	{
		return new Conic(r0, v0, U, t0 * (1d / TICKS_PER_SEC));
	}

	private StateVector solveKepler(long time)
	{
		double thistime = time * (1d / TICKS_PER_SEC);
		StateVector svec = getPrivateConic().getStateVectorAtTime(thistime);
		return svec;
	}

	// hotspot

	public Vector3d getPos(long time)
	{
		if (time == t0)
			return new Vec3d(r0);
		if (time > t0 + timestep || oldy0 == null)
		{
			StateVector res = solveKepler(time);
			return new Vec3d(res.r);
		}
		//CubicSpline3d cs3d = new CubicSpline3d(r0, posevent.y0.a, v0, posevent.y0.b);
		//return cs3d.f((time-t0)*1d/timestep);
		solve(time, false);
		return new Vec3d(laststate.a);
	}

	public Vector3d getVel(long time)
	{
		if (time == t0)
			return new Vec3d(v0);
		if (time > t0 + timestep || oldy0 == null)
		{
			StateVector res = solveKepler(time);
			return new Vec3d(res.v);
		}
		//CubicSpline3d cs3d = new CubicSpline3d(r0, posevent.y0.a, v0, posevent.y0.b);
		//return cs3d.fp((time-t0)*1d/timestep);
		solve(time, false);
		return new Vec3d(laststate.b);
	}

	public Orientation getOrt(long time)
	{
		if (!integrateAngular || time > t0 + timestep || oldy0 == null)
			return super.getOrt(time);
		else
		{
			if (time == t0)
				return new Orientation(ort0);
			solve(time, false);
			return new Orientation(laststate.c);
		}
	}

	public void setOrientation(Orientation ort)
	{
		if (!integrateAngular)
			super.setOrientation(ort);
		else
		{
			refresh();
			ort0.set(ort);
			ort0.normalize();
		}
	}

	// since angvel is integrated, we gotta get the time ya know
	public Vector3d getAngularVelocity()
	{
		long time = getGame().time();
		if (!integrateAngular || time > t0 + timestep || oldy0 == null)
			return super.getAngularVelocity();
		else
		{
			if (time == t0)
				return new Vec3d(angvel);
			solve(time, false);
			return new Vec3d(laststate.d);
		}
	}

	//

	/**
	  * Same as super.isOrbitable() and wheels aren't touching
	  */
	public boolean isOrbitable(boolean b)
	{
		return (wheels_mask == 0) && super.isOrbitable(b);
	}

	/**
	  * We don't ever change out of Cowell
	  */
	public boolean checkPerturbs()
	{
		return false;
	}

	/**
	  * See if we escape the current body's influence
	  */
	public boolean checkInfluenceExit()
	{
		if (refinfrad > 0 && r0.lengthSquared() > refinfrad * refinfrad)
		{
			AlertEvent.postAlert(getGame(), getThing() + " exiting influence of " + ref);
			changeParent(getParent().getParent());
			return true;
		}
		else
			return false;
	}

	/**
	  * See if we escape the current body's influence
	  */
	public boolean checkInfluenceEnter()
	{
		Iterator it = getDefaultPerturbations();
		while (it.hasNext())
		{
			Perturbation pert = (Perturbation) it.next();
			if (pert instanceof ThirdBodyPerturbation)
			{
				ThirdBodyPerturbation pert3 = (ThirdBodyPerturbation) pert;
				UniverseThing body = pert3.getThirdBody();
				// is this body's parent our reference, and are we inside it?
				if (pert3.isInsideInfluence() && body.getParent() == ref)
				{
					AlertEvent.postAlert(getGame(), getThing() + " entering influence of " + body);
					changeParent(body);
					return true;
				}
			}
		}
		return false;
	}

	/**
	  * Calculate contact forces and add them to pf.
	  * Assumes wheels_mask != 0
	  */
	protected void addContactForces(
		PerturbForce pf,
		Vector3d r0,
		Vector3d v0,
		Orientation ort0,
		Vector3d w0,
		long time)
	{
		if (wheels_mask == 0)
			return;
		
		if (debug3)
			System.out.println("addContactForces(): t=" + time + ", mask=" + wheels_mask);
		
		Planet planet = (Planet) ref;
		Vector3d nml = planet.getNormalAt(r0, AstroUtil.tick2dbl(time));
		float mass = (float) thing.getMass(time);

		Vector3d r = new Vec3d();
		Vector3d v = new Vec3d();
		Vector3f pt = new Vector3f();
		Vector3f fdir = new Vector3f();

		// iterate thru each contact point
		int mask = wheels_mask;
		int newmask = wheels_detected;
		ContactPoint[] cpts = ship.getContactPoints();

		int i = 0;
		while (mask != 0)
		{
			// is this wheel contacted?
			if ((mask & 1) != 0)
			{
				if (i >= cpts.length)
					break;
				// make sure extended is below ground
				Vector3f stpt = cpts[i].extpos;
				pt.set(stpt);
				ort0.transform(pt);
				// get position
				r.set(pt);
				r.add(r0);

				double rl = r.length();

				double dt = time * (1d / Constants.TICKS_PER_SEC);
				double pelev = planet.getRadius() + planet.getElevationAt(r, dt);
				double agl = rl - pelev;
				// make sure below elevation
				if (agl < 0)
				{
					newmask |= (1 << i);
					// what direction?
					fdir.set(nml);
					// get point velocity at extpos
					v.set(pt);
					v.cross(w0, v);
					v.add(v0);
					v.sub(planet.getAirVelocity(r));
					double vl2 = v.lengthSquared();
					if (vl2 < min_wheelvel2_detected)
						min_wheelvel2_detected = vl2;
					double vertvel = v.dot(nml);
					// put some force on this wheel
					// how much?
					float Kspring;
					Kspring = cpts[i].Kspring;
					agl = Math.max(agl, -cpts[i].maxCompress);
					float Kdamping = cpts[i].Kdamping;
					float forcemag = (float) (Kspring * agl + vertvel * Kdamping);
					//if (agl < -cpts[i].maxCompress)
						//forcemag *= MAX_COMPRESS_SCALE;
					// apply force
					if (forcemag < 0)
						pf.addOffsetForce(fdir, pt, -forcemag);
					// static friction
					float Kstatic = cpts[i].Kstatic;
					if (Kstatic > 0)
					{
						fdir.set(v);
						pf.addOffsetForce(fdir, pt, -Kstatic * mass); //todo
					}
					// todo: rolling friction (Z dir)
					if (debug3)
					{
						System.out.println(
							i + ": agl= " + agl + " vvel= " + vertvel + " force= " + forcemag);
					}
				}
			}
			mask >>>= 1;
			i++;
		}
		wheels_detected = newmask;
	}

	/**
	  * Get all the perturbation forces,
	  * including the main body perturbation and contact forces (if any)
	  */
	protected PerturbForce getAllPerturbForces(
		Vector3d r,
		Vector3d v,
		Orientation ort,
		Vector3d w,
		long time)
	{
		in_routine = true; // avoid recursion
		try
		{
			PerturbForce pf = super.getPerturbForces(r, v, ort, w, time);
			// add primary body perturbation
			if (bodyperturb != null)
				bodyperturb.addPerturbForce(pf, r, v, ort, w, time);
			if (wheels_mask != 0)
				addContactForces(pf, r, v, ort, w, time);
			return pf;
		}
		finally
		{
			in_routine = false;
		}
	}

	public PerturbForce getLastPerturbForce()
	{
		return curpforce;
	}

	public double getLastIntegrationError()
	{
		return lastIntegrationError;
	}

	public String getType()
	{
		return "cowell";
	}
	
	public long getLastT0()
	{
		return t0;
	}
	
	public StateVector getLastInitialState()
	{
		return new StateVector(oldy0.a, oldy0.b);
	}

	//

	public static boolean debug = false;
	public static boolean debug2 = false;
	public static boolean debug3 = false;

	// SETTINGS

	private static int MIN_TIMESTEP;
	private static int LARGE_TIMESTEP;
	private static int SMALL_TIMESTEP;
	private static int MAX_TIMESTEP;
	private static double LO_ERROR_THRESH;
	private static double HI_ERROR_THRESH;
	private static double BAD_ERROR_THRESH;
	private static float COARSE_ERROR_FACTOR;
	private static double MIN_SURFACE_VEL_2;
	private static boolean DO_UCE_WARNING;

	static SettingsGroup settings = new SettingsGroup(CowellTrajectory.class, "Cowell")
	{
		public void updateSettings()
		{
			MIN_TIMESTEP = getInt("MinTimestep", 2);
			LARGE_TIMESTEP = getInt("LargeTimestep", 13*8);
			SMALL_TIMESTEP = getInt("SmallTimestep", 64);
			MAX_TIMESTEP = getInt("MaxTimestep", (int) TICKS_PER_SEC * 32768) / 2;
			LO_ERROR_THRESH = getDouble("LoErrorThresh", 1e-10);
			HI_ERROR_THRESH = getDouble("HiErrorThresh", 1e-9);
			BAD_ERROR_THRESH = getDouble("BadErrorThresh", 1e-3);
			COARSE_ERROR_FACTOR = getFloat("CoarseErrorScale", 0.01f);
			MIN_SURFACE_VEL_2 = AstroUtil.sqr(getDouble("MinStoppingVel", 0.0005));
			DO_UCE_WARNING = getBoolean("UCEWarning", true);
		}
	};

}
