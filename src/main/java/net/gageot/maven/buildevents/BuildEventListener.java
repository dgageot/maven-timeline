/**
 * Copyright (C) 2013 david@gageot.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.gageot.maven.buildevents;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.apache.maven.execution.*;
import org.apache.maven.plugin.*;

import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.context.*;

public class BuildEventListener extends AbstractExecutionListener {
	private final File output;
	private final Map<String, Long> startTimes = new ConcurrentHashMap<String, Long>();
	private final Map<String, Long> endTimes = new ConcurrentHashMap<String, Long>();

	public BuildEventListener(File output) {
		this.output = output;
	}

	@Override
	public void mojoStarted(ExecutionEvent event) {
		MojoExecution mojo = event.getMojoExecution();
		String goal = mojo.getGoal();
		String phase = mojo.getLifecyclePhase();
		String project = event.getProject().getArtifactId();

		startTimes.put(project + "/" + phase + "/" + goal, System.currentTimeMillis());
	}

	@Override
	public void mojoSkipped(ExecutionEvent event) {
		mojoEnd(event);
	}

	@Override
	public void mojoSucceeded(ExecutionEvent event) {
		mojoEnd(event);
	}

	@Override
	public void mojoFailed(ExecutionEvent event) {
		mojoEnd(event);
	}

	private void mojoEnd(ExecutionEvent event) {
		MojoExecution mojo = event.getMojoExecution();
		String goal = mojo.getGoal();
		String phase = mojo.getLifecyclePhase();
		String project = event.getProject().getArtifactId();

		endTimes.put(project + "/" + phase + "/" + goal, System.currentTimeMillis());
	}

	@Override
	public void sessionEnded(ExecutionEvent event) {
		report();
	}

	public void report() {
		long buildStartTime = Long.MAX_VALUE;
		for (Long start : startTimes.values()) {
			buildStartTime = Math.min(buildStartTime, start);
		}

		long buildEndTime = 0;
		for (Long end : endTimes.values()) {
			buildEndTime = Math.max(buildEndTime, end);
		}

		List<Measure> measures = new ArrayList<Measure>();
		for (String key : startTimes.keySet()) {
			String[] keyParts = key.split("/");

			Measure measure = new Measure();
			measure.project = keyParts[0];
			measure.phase = keyParts[1];
			measure.goal = keyParts[2];
			measure.left = ((startTimes.get(key) - buildStartTime) * 10000L) / (buildEndTime - buildStartTime);
			measure.width = (((endTimes.get(key) - buildStartTime) * 10000L) / (buildEndTime - buildStartTime)) - measure.left;
			measures.add(measure);
		}

		Collections.sort(measures);

		try {
			String html = new Handlebars().compile("template").apply(Context.newBuilder(measures).resolver(FieldValueResolver.INSTANCE).build());
			write(html);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void write(String message) throws IOException {
		File path = output.getParentFile();
		if (!path.exists()) {
			path.mkdirs();
		}

		FileWriter writer = new FileWriter(output);
		writer.write(message);
		writer.close();
	}

	public static class Measure implements Comparable<Measure> {
		String project;
		String phase;
		String goal;
		Long left;
		Long width;

		@Override
		public int compareTo(Measure other) {
			return left.compareTo(other.left);
		}
	}
}