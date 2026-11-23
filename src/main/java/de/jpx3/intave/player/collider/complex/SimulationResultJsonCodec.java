/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.player.collider.complex;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.JsonStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Motion;

import java.io.IOException;
import java.io.UncheckedIOException;

final class SimulationResultJsonCodec implements StreamCodec<JsonReader, JsonWriter, SimulationResult> {
	public static final SimulationResultJsonCodec INSTANCE = new SimulationResultJsonCodec();

	private SimulationResultJsonCodec() {
	}

	@Override
	public void encode(JsonWriter writer, SimulationResult result) {
		try {
			boolean valid = result != null && result.isValid();
			writer.beginObject();
			writer.name("valid").value(valid);
			if (valid) {
				writer.name("actualMotion");
				JsonStreamCodecs.writeNullable(writer, Motion.JSON_CODEC, result.actualMotion());
				writer.name("offsetMotion");
				JsonStreamCodecs.writeNullable(writer, Motion.JSON_CODEC, result.offsetMotion());
				writer.name("intermittentResult");
				JsonStreamCodecs.writeNullable(writer, Motion.JSON_CODEC, result.intermittentResult());
				writer.name("onGround").value(result.onGround());
				writer.name("collidedHorizontally").value(result.collidedHorizontally());
				writer.name("collidedVertically").value(result.collidedVertically());
				writer.name("resetMotionX").value(result.resetMotionX());
				writer.name("resetMotionZ").value(result.resetMotionZ());
				writer.name("step").value(result.step());
				writer.name("edgeSneak").value(result.edgeSneak());
				writer.name("stepHeight");
				JsonStreamCodecs.writeDouble(writer, result.stepHeightThisMove());
			}
			writer.endObject();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Override
	public SimulationResult decode(JsonReader reader) {
		try {
			boolean valid = false;
			Motion actualMotion = null;
			Motion offsetMotion = null;
			Motion intermittentResult = null;
			boolean onGround = false;
			boolean collidedHorizontally = false;
			boolean collidedVertically = false;
			boolean resetMotionX = false;
			boolean resetMotionZ = false;
			boolean step = false;
			boolean edgeSneak = false;
			double stepHeight = 0;
			reader.beginObject();
			while (reader.hasNext()) {
				switch (reader.nextName()) {
					case "valid":
						valid = reader.nextBoolean();
						break;
					case "actualMotion":
						actualMotion = JsonStreamCodecs.readNullable(reader, Motion.JSON_CODEC);
						break;
					case "offsetMotion":
						offsetMotion = JsonStreamCodecs.readNullable(reader, Motion.JSON_CODEC);
						break;
					case "intermittentResult":
						intermittentResult = JsonStreamCodecs.readNullable(reader, Motion.JSON_CODEC);
						break;
					case "onGround":
						onGround = reader.nextBoolean();
						break;
					case "collidedHorizontally":
						collidedHorizontally = reader.nextBoolean();
						break;
					case "collidedVertically":
						collidedVertically = reader.nextBoolean();
						break;
					case "resetMotionX":
						resetMotionX = reader.nextBoolean();
						break;
					case "resetMotionZ":
						resetMotionZ = reader.nextBoolean();
						break;
					case "step":
						step = reader.nextBoolean();
						break;
					case "edgeSneak":
						edgeSneak = reader.nextBoolean();
						break;
					case "stepHeight":
						stepHeight = JsonStreamCodecs.readDouble(reader);
						break;
					default:
						reader.skipValue();
				}
			}
			reader.endObject();
			if (!valid) {
				return SimulationResult.invalid();
			}
			if (offsetMotion == null) {
				throw new IllegalStateException("Valid simulation result is missing offsetMotion");
			}
			return new SimulationResult(
				actualMotion, offsetMotion, intermittentResult,
				onGround, collidedHorizontally, collidedVertically,
				resetMotionX, resetMotionZ, step, edgeSneak, stepHeight
			);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

}
