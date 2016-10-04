/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.analysis.algorithms;

import java.util.PriorityQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of 2D watershed transform.
 * 
 * TODO: Implement any further optimizations added to the ImageJ version
 * 
 * @author Pete Bankhead
 *
 */
public class Watershed {
	
	final private static Logger logger = LoggerFactory.getLogger(Watershed.class);
	
	public static void doWatershed(final SimpleImage ip, final SimpleModifiableImage ipLabels, final boolean conn8) {
		doWatershed(ip, ipLabels, Double.NEGATIVE_INFINITY, conn8);
	}
	
	public static void doWatershed(final SimpleImage ip, final SimpleModifiableImage ipLabels, final double minThreshold, final boolean conn8) {
		
		long startTime = System.currentTimeMillis();
		
		int width = ip.getWidth();
		int height = ip.getHeight();
		
		// Create & initialize a priority queue
		WatershedQueueWrapper queue = new WatershedQueueWrapper(ip, ipLabels, minThreshold);
		
		// Process the queue
		while (!queue.isEmpty()) {
			PixelWithValue pwv = queue.poll();
			float lastLabel;
			if (conn8)
				lastLabel = getNeighborLabels8(ipLabels, pwv.x, pwv.y, width, height);
			else
				lastLabel = getNeighborLabels4(ipLabels, pwv.x, pwv.y, width, height);
			if (Float.isNaN(lastLabel))
				continue;
			ipLabels.setValue(pwv.x, pwv.y, lastLabel);
			if (conn8)
				addNeighboursToQueue8(queue, pwv.x, pwv.y, width, height);
			else
				addNeighboursToQueue4(queue, pwv.x, pwv.y, width, height);
		}
		
		long endTime = System.currentTimeMillis();
		logger.trace(String.format("Watershed time taken: %.2fs", (endTime - startTime)/1000.0));	
	}
	
	
	public static float getNeighborLabels4(final SimpleImage ipLabels, final int x, final int y, final int w, final int h) {
		float lastLabel = Float.NaN;
		if (x > 0) {
			float label = ipLabels.getValue(x-1, y);
			if (label != 0) {
//				if (Float.isNaN(lastLabel))
					lastLabel = label;
//				else if (lastLabel != label)
//					return Float.NaN;
			}
		}
		if (x < w-1) {
			float label = ipLabels.getValue(x+1, y);
			if (label != 0) {
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		if (y > 0) {
			float label = ipLabels.getValue(x, y-1);
			if (label != 0) {
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		if (y < h-1) {
			float label = ipLabels.getValue(x, y+1);
			if (label != 0) {
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		return lastLabel;
	}
	
	
	public static void addNeighboursToQueue4(final WatershedQueueWrapper queue, final int x, final int y , int w, final int h) {
		queue.add(x, y-1);
		queue.add(x-1, y);
		queue.add(x+1, y);
		queue.add(x, y+1);
	}
	
	
	public static float getNeighborLabels8(final SimpleImage ipLabels, final int x, final int y, final int w, final int h) {
		float lastLabel = Float.NaN;
		for (int yy = Math.max(y-1, 0); yy <= Math.min(h-1, y+1); yy++) {
			for (int xx = Math.max(x-1, 0); xx <= Math.min(w-1, x+1); xx++) {
				if (xx == x && yy == y)
					continue;
				float label = ipLabels.getValue(xx, yy);
				// TODO: CONSIDER USE OF -1 BOUNDARIES
				if (label <= 0)
					continue;
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		return lastLabel;
	}
	
	
	public static void addNeighboursToQueue8(final WatershedQueueWrapper queue, final int x, final int y, final int w, final int h) {
		queue.add(x-1, y-1);
		queue.add(x, y-1);
		queue.add(x+1, y-1);
		
		queue.add(x-1, y);
		queue.add(x+1, y);

		queue.add(x-1, y+1);
		queue.add(x, y+1);
		queue.add(x+1, y+1);
	}
	
	
	
	
}


class WatershedQueueWrapper {

	private PriorityQueue<PixelWithValue> queue = new PriorityQueue<>();
	private boolean[] queued = null;
	private long counter = 0;//Long.MIN_VALUE;
	private int width, height;
	private SimpleImage ip;
	
	public WatershedQueueWrapper(SimpleImage ip, SimpleImage ipLabels, double minThreshold) {
		this.ip = ip;
		this.width = ip.getWidth();
		this.height = ip.getHeight();
		// Keep a record of already-queued pixels
		queued = new boolean[width * height];
		// Loop through and populate the queue sensibly; background assumed to be zero
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float val = ip.getValue(x, y);
				// Mark below-threshold pixels as queued (even if they aren't...) to indicate they shouldn't be added later
				if (val <= minThreshold) {
					queued[y * width + x] = true;
					continue;
				}
				// Mark already-labeled pixels as queued as well,
				// and add pixels immediately adjacent to a labeled pixel to the queue
				if (ipLabels.getValue(x, y) != 0)
					queued[y * width + x] = true;
				else {
					boolean front = (x > 0 && ipLabels.getValue(x-1, y) != 0) ||
							(y > 0 && ipLabels.getValue(x, y-1) != 0) ||
							(x < width-1 && ipLabels.getValue(x+1, y) != 0) ||
							(y > height-1 && ipLabels.getValue(x, y+1) != 0);
					if (front) {
						queued[y * width + x] = true;
						queue.add(new PixelWithValue(x, y, val, ++counter));
					}
				}
			}			
		}
	}
	
	public final void add(int x, int y) {
		// Don't add to the queue twice
		if (!mayAddToQueue(x, y))
			return;
		addWithoutCheck(x, y, ip.getValue(x, y));
	}
	
	protected final void addWithoutCheck(int x, int y, float val) {
		// Add, while storing a count variable, effectively turning the PriorityQueue into a FIFO queue whenever values are equal
		// This is necessary to produce reasonable-looking watershed results where there are plateaus (i.e. pixels with the same value)
		queue.add(new PixelWithValue(x, y, val, ++counter));
		// Keep track of the fact this has been queued - won't need it again
		queued[y * width + x] = true;
	}
	
//	public final boolean mayAddToQueue(int x, int y) {
//		return !queued[y * width + x];
//	}

	public final boolean mayAddToQueue(int x, int y) {
		return x >= 0 && x < width && y >= 0 && y < height && !queued[y * width + x];
	}

	public final PixelWithValue poll() {
		return queue.poll();
	}
	
	public final boolean isEmpty() {
		return queue.isEmpty();
	}
	
}


class PixelWithValue implements Comparable<PixelWithValue> {
	
	public int x, y;
	public float value;
	private long count;
	
	public PixelWithValue(final int x, final int y, final float value, final long count) {
		this.x = x;
		this.y = y;
		this.value = value;
		this.count = count;
//		System.out.println("My count: " + count);
	}

	@Override
	public int compareTo(final PixelWithValue pwv) {
		// Profiling indicates that the many comparisons are the slowest part of the algorithm...
		if (value < pwv.value) {
			return 1;
		}
		else if (value > pwv.value) {
			return -1;
		}
		return count > pwv.count ? 1 : -1;
		
//		// Profiling indicates that the many comparisons are the slowest part of the algorithm...
//		if (value > pwv.value)
//			return 1;
//		else if (value < pwv.value)
//			return -1;
//		return count > pwv.count ? 1 : -1;
	}
	
}