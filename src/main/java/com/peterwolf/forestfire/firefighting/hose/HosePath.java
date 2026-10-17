package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Cached terrain-following hose geometry with precomputed path length.
 */
public final class HosePath {
	public static final Codec<HosePath> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		HoseControlPoint.CODEC.listOf().fieldOf("points").forGetter(p -> p.points),
		Codec.DOUBLE.fieldOf("length").forGetter(p -> p.pathLength)
	).apply(instance, HosePath::new));

	private final List<HoseControlPoint> points;
	private final double pathLength;

	public HosePath(List<HoseControlPoint> points, double pathLength) {
		this.points = Collections.unmodifiableList(new ArrayList<>(points));
		this.pathLength = Math.max(0.0, pathLength);
	}

	public static HosePath of(List<HoseControlPoint> points) {
		return new HosePath(points, computeLength(points));
	}

	public static HosePath empty() {
		return new HosePath(List.of(), 0.0);
	}

	public List<HoseControlPoint> points() {
		return points;
	}

	public double pathLength() {
		return pathLength;
	}

	/** Integer block length used for inventory consumption (ceil). */
	public int blockLength() {
		return Math.max(1, (int) Math.ceil(pathLength));
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public Vec3 start() {
		return points.isEmpty() ? Vec3.ZERO : points.getFirst().position();
	}

	public Vec3 end() {
		return points.isEmpty() ? Vec3.ZERO : points.getLast().position();
	}

	/**
	 * Fixed terrain portion (everything except the last dynamic endpoint).
	 */
	public HosePath fixedPrefix() {
		if (points.size() <= 1) {
			return this;
		}
		return of(points.subList(0, points.size() - 1));
	}

	/**
	 * Replace the final endpoint while keeping terrain anchors.
	 */
	public HosePath withDynamicEndpoint(Vec3 endpoint, HosePointType type) {
		if (points.isEmpty()) {
			return of(List.of(new HoseControlPoint(endpoint, type)));
		}
		List<HoseControlPoint> next = new ArrayList<>(points.size());
		for (int i = 0; i < points.size() - 1; i++) {
			next.add(points.get(i));
		}
		next.add(new HoseControlPoint(endpoint, type));
		return of(next);
	}

	public static double computeLength(List<HoseControlPoint> pts) {
		if (pts == null || pts.size() < 2) {
			return 0.0;
		}
		double sum = 0.0;
		for (int i = 1; i < pts.size(); i++) {
			sum += pts.get(i - 1).position().distanceTo(pts.get(i).position());
		}
		return sum;
	}
}
