package org.xronos.orcc.analysis;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import net.sf.orcc.df.Action;
import net.sf.orcc.df.Instance;
import net.sf.orcc.df.Network;
import net.sf.orcc.graph.Vertex;

public class WeightWriter {

	private final Map<Instance, Map<Action, TimeGoDone>> execution;
	private final String filePath;
	private final Network network;

	public WeightWriter(Map<Instance, Map<Action, TimeGoDone>> execution,
			Network network, String filePath) {
		this.execution = execution;
		this.network = network;
		this.filePath = filePath;
	}

	private Integer actionState(ArrayList<Integer> previousGoDone,
			ArrayList<Integer> goDone) {
		Integer state = 0;

		Integer previousState = goDoneState(previousGoDone);
		Integer currentState = goDoneState(goDone);

		if (previousState == 0 && currentState == 0) {
			state = 0;
		} else if (previousState == 0 && currentState == 1) {
			state = 1;
		} else if (previousState == 0 && currentState == 2) {
			state = 2;
		} else if (previousState == 0 && currentState == 3) {
			state = 3;
		} else if (previousState == 1 && currentState == 0) {
			state = 4;
		} else if (previousState == 2 && currentState == 0) {
			state = 5;
		} else if (previousState == 2 && currentState == 1) {
			state = 6;
		} else if (previousState == 2 && currentState == 3) {
			state = 7;
		} else if (previousState == 3 && currentState == 0) {
			state = 8;
		} else if (previousState == 3 && currentState == 1) {
			state = 9;
		} else if (previousState == 3 && currentState == 3) {
			state = 10;
		}

		return state;

	}

	private Integer goDoneState(ArrayList<Integer> goDone) {
		Integer state = 0;

		if (goDone.get(0).equals(0) && goDone.get(1).equals(0)) {
			state = 0;
		} else if (goDone.get(0).equals(0) && goDone.get(1).equals(1)) {
			state = 1;
		} else if (goDone.get(0).equals(1) && goDone.get(1).equals(0)) {
			state = 2;
		} else if (goDone.get(0).equals(1) && goDone.get(1).equals(1)) {
			state = 3;
		}
		return state;
	}

	public void writeProtobuf() {
		File protoFile = new File(filePath);
		try {
			FileOutputStream out = new FileOutputStream(protoFile);

			for (Vertex vertex : network.getVertices()) {
				if (vertex instanceof Instance) {
					Instance instance = (Instance) vertex;
					Map<Action, TimeGoDone> actionsGoDone = execution
							.get(instance);
					for (Action action : instance.getActor().getActions()) {
						TimeGoDone timeGoDone = actionsGoDone.get(action);

						ArrayList<Integer> previousGoDone = timeGoDone.get(0);
						@SuppressWarnings("unused")
						Integer start = 0;
						Integer stop = 0;
						for (int i = 0; i < timeGoDone.size(); i++) {
							Integer timeBy100 = i * 100;
							ArrayList<Integer> goDone = timeGoDone
									.get(timeBy100);
							// ActionWeight.Builder actionWeight = ActionWeight
							// .newBuilder();

							Integer state = actionState(previousGoDone, goDone);

							switch (state) {
							case 0:
								break;
							case 1:
								stop = timeBy100;
								// actionWeight
								// .setInstanceName(
								// instance.getSimpleName())
								// .setActionName(action.getName())
								// .setNumClock(stop - start);
								// actionWeight.build().writeDelimitedTo(out);
								break;
							case 2:
								start = timeBy100;
								break;
							case 3:
								start = timeBy100;
								break;
							case 4:
								stop = timeBy100;
								// actionWeight
								// .setInstanceName(
								// instance.getSimpleName())
								// .setActionName(action.getName())
								// .setNumClock(stop - start);
								// actionWeight.build().writeDelimitedTo(out);
								break;
							case 5:
								stop = timeBy100;
								// actionWeight
								// .setInstanceName(
								// instance.getSimpleName())
								// .setActionName(action.getName())
								// .setNumClock(stop - start);
								// actionWeight.build().writeDelimitedTo(out);
								break;
							case 6:
								stop = timeBy100;
								// actionWeight
								// .setInstanceName(
								// instance.getSimpleName())
								// .setActionName(action.getName())
								// .setNumClock(stop - start);
								// actionWeight.build().writeDelimitedTo(out);
								break;
							case 7:
								stop = timeBy100;
								// actionWeight
								// .setInstanceName(
								// instance.getSimpleName())
								// .setActionName(action.getName())
								// .setNumClock(stop - start);
								// actionWeight.build().writeDelimitedTo(out);
								start = stop;
								break;
							case 8:
								stop = timeBy100;
								// actionWeight
								// .setInstanceName(
								// instance.getSimpleName())
								// .setActionName(action.getName())
								// .setNumClock(stop - start);
								// actionWeight.build().writeDelimitedTo(out);
								break;
							case 9:
								stop = timeBy100;
								// actionWeight
								// .setInstanceName(
								// instance.getSimpleName())
								// .setActionName(action.getName())
								// .setNumClock(stop - start);
								// actionWeight.build().writeDelimitedTo(out);
								start = stop;
								break;
							case 10:
								stop = timeBy100;
								// actionWeight
								// .setInstanceName(
								// instance.getSimpleName())
								// .setActionName(action.getName())
								// .setNumClock(stop - start);
								// actionWeight.build().writeDelimitedTo(out);
								start = stop;
								break;
							default:
								break;
							}

							previousGoDone = goDone;

						}

					}
				}
			}
			// Close the file
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
