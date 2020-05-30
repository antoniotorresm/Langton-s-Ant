package com.camoga.ant;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map.Entry;

import org.apache.commons.collections4.keyvalue.MultiKey;

import com.camoga.ant.Level.Chunk;
import com.camoga.ant.test.hex.IAnt;
import com.camoga.ant.test.hex.IRule;

public class Ant implements IAnt {
	int dir;
	int xc,yc;
	int x, y;

	Chunk chunk;
	int state = 0;
	
	static final int[][] directions = new int[][] {{0,-1},{1,0},{0,1},{-1,0}};
	
	byte[] states;
	public boolean saveState = false;
	public int repeatLength = 0;
	public long index = 1;
	
	public long minHighwayPeriod = 0;  // This is the final period length
	public boolean PERIODFOUND = false;
	
	private Worker worker;
	private Rule rule;
	
	public Ant(Worker worker) {
		this.worker = worker;
		rule = new Rule();
	}
	
	public void init(long rule, long iterations) {
		int stateslen = iterations == -1 ? 200000000:(int) Math.min(Math.max(5000000,iterations/(int)Settings.repeatcheck*2), 200000000);
		if(states == null || states.length != stateslen) states = new byte[stateslen];
		this.rule.createRule(rule);
		x = 0;
		y = 0;
		xc = 0;
		yc = 0;		
		dir = 0;
		state = 0;
		saveState = false;
		repeatLength = 0;
		index = 1;
		minHighwayPeriod = 0;
		PERIODFOUND = false;
		chunk = worker.level.chunks.get(0,0);
	}
	
	/**
	 * 
	 * @return true if ant forms a highway
	 */
	public int move() {
		int i = 0;
		for(; i < Settings.itpf; i++) {			
			if(saveState) {
				byte s1 = (byte)(dir<<6 | state); //Only works for rules with <= 64 colors
				if(index < states.length) states[(int) index] = s1;
				index++;
				if(states[repeatLength]!=s1) {
					repeatLength = 0;
					minHighwayPeriod = index;
				} else {
					repeatLength++;
					if(repeatLength == states.length || repeatLength > Settings.repeatcheck*minHighwayPeriod) {
						PERIODFOUND = true;
						saveState = false;
						break;
					}
				}
			}
			
			if(x > Settings.cSIZEm) {
				x = 0;
				xc++;
				chunk = worker.level.getChunk(xc, yc);
			} else if(x < 0) {
				x = Settings.cSIZEm;
				xc--;
				chunk = worker.level.getChunk(xc, yc);
			} else if(y > Settings.cSIZEm) {
				y = 0;
				yc++;
				chunk = worker.level.getChunk(xc, yc);
			} else if(y < 0) {
				y = Settings.cSIZEm;
				yc--;
				chunk = worker.level.getChunk(xc, yc);
			}
			
			int index = x|(y<<Settings.cPOW);
			state = chunk.cells[index];
			dir = (dir + rule.turn[state])&0b11;
			if(++chunk.cells[index] == rule.size) chunk.cells[index] = 0;
			
			x += directions[dir][0];
			y += directions[dir][1];
			
			//OPTIMIZE (chunk coordinates can only change if x/y = 0/cSIZE)
//			xc += x>>Settings.cPOW;
//			yc += y>>Settings.cPOW;
//			x = x&Settings.cSIZEm;
//			y = y&Settings.cSIZEm;
		}
		return i;
	}
	
	public void saveState() {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(rule.rule+".state"));
			oos.writeLong(rule.rule);
			oos.writeLong(worker.getIterations());
			oos.writeInt(dir);
			oos.writeInt(state);
			oos.writeInt(x);
			oos.writeInt(y);
			oos.writeInt(xc);
			oos.writeInt(yc);
			oos.writeBoolean(saveState);
			if(saveState) {
				oos.writeLong(index);
				oos.writeInt(repeatLength);
				oos.writeLong(minHighwayPeriod);
				oos.write(states);
			}
			oos.writeByte(Settings.cPOW);
			oos.writeInt(worker.getLevel().chunks.size());
			for(Entry<MultiKey<? extends Integer>, Chunk> c : worker.getLevel().chunks.entrySet()) {
				MultiKey<? extends Integer> key = c.getKey();
				oos.writeInt(key.getKey(0));
				oos.writeInt(key.getKey(1));
				oos.write(c.getValue().cells);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public long getPeriod() {
		return minHighwayPeriod;
	}

	public boolean findingPeriod() {
		return saveState;
	}

	public boolean periodFound() {
		return PERIODFOUND;
	}

	public void setFindingPeriod(boolean b) {
		saveState = b;
	}

	public void initPeriodFinding() {
		states[0] = (byte)(dir<<6 | state);
	}

	public int getXC() {
		return xc;
	}

	public int getYC() {
		return yc;
	}
	
	public IRule getRule() {
		return rule;
	}
}