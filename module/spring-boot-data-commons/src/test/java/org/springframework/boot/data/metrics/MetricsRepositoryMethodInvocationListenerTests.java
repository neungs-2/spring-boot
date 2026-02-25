/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.data.metrics;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocation;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocationResult;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocationResult.State;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MetricsRepositoryMethodInvocationListener}.
 *
 * @author Phillip Webb
 */
class MetricsRepositoryMethodInvocationListenerTests {

	private static final String REQUEST_METRICS_NAME = "repository.invocations";

	private SimpleMeterRegistry registry;

	private ObservationRegistry observationRegistry;

	private RecordingObservationHandler observationHandler;

	private MetricsRepositoryMethodInvocationListener listener;

	@BeforeEach
	void setup() {
		MockClock clock = new MockClock();
		this.registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
		this.observationRegistry = ObservationRegistry.create();
		this.observationHandler = new RecordingObservationHandler();
		this.observationRegistry.observationConfig().observationHandler(this.observationHandler);
		this.listener = new MetricsRepositoryMethodInvocationListener(() -> this.registry,
				new DefaultRepositoryTagsProvider(), REQUEST_METRICS_NAME, AutoTimer.ENABLED,
				() -> this.observationRegistry);
	}

	@Test
	void afterInvocationWhenNoTimerAnnotationsAndNoAutoTimerDoesNothing() {
		this.listener = new MetricsRepositoryMethodInvocationListener(() -> this.registry,
				new DefaultRepositoryTagsProvider(), REQUEST_METRICS_NAME, null);
		this.listener.afterInvocation(createInvocation(NoAnnotationsRepository.class));
		assertThat(this.registry.find(REQUEST_METRICS_NAME).timers()).isEmpty();
	}

	@Test
	void afterInvocationWhenTimedMethodRecordsMetrics() {
		this.listener.afterInvocation(createInvocation(TimedMethodRepository.class));
		assertMetricsContainsTag("state", "SUCCESS");
		assertMetricsContainsTag("tag1", "value1");
	}

	@Test
	void afterInvocationWhenTimedClassRecordsMetrics() {
		this.listener.afterInvocation(createInvocation(TimedClassRepository.class));
		assertMetricsContainsTag("state", "SUCCESS");
		assertMetricsContainsTag("taga", "valuea");
	}

	@Test
	void afterInvocationWhenAutoTimedRecordsMetrics() {
		this.listener.afterInvocation(createInvocation(NoAnnotationsRepository.class));
		assertMetricsContainsTag("state", "SUCCESS");
	}

	@Test
	void afterInvocationWhenObservedMethodCreatesObservation() {
		this.listener.afterInvocation(createInvocation(ObservedMethodRepository.class));
		assertThat(this.observationHandler.observationNames()).contains("repository.observed.method");
	}

	@Test
	void afterInvocationWhenObservedClassCreatesObservation() {
		this.listener.afterInvocation(createInvocation(ObservedClassRepository.class));
		assertThat(this.observationHandler.observationNames()).contains("repository.observed.class");
	}

	@Test
	void afterInvocationWhenNoObservedAnnotationsDoesNotCreateObservation() {
		this.listener.afterInvocation(createInvocation(NoAnnotationsRepository.class));
		assertThat(this.observationHandler.observationNames()).isEmpty();
	}

	private void assertMetricsContainsTag(String tagKey, String tagValue) {
		assertThat(this.registry.get(REQUEST_METRICS_NAME).tag(tagKey, tagValue).timer().count()).isOne();
	}

	private RepositoryMethodInvocation createInvocation(Class<?> repositoryInterface) {
		Method method = ReflectionUtils.findMethod(repositoryInterface, "findById", long.class);
		assertThat(method).isNotNull();
		RepositoryMethodInvocationResult result = mock(RepositoryMethodInvocationResult.class);
		given(result.getState()).willReturn(State.SUCCESS);
		return new RepositoryMethodInvocation(repositoryInterface, method, result, 0);
	}

	interface NoAnnotationsRepository extends Repository<Example, Long> {

		Example findById(long id);

	}

	interface TimedMethodRepository extends Repository<Example, Long> {

		@Timed(extraTags = { "tag1", "value1" })
		Example findById(long id);

	}

	@Timed(extraTags = { "taga", "valuea" })
	interface TimedClassRepository extends Repository<Example, Long> {

		Example findById(long id);

	}

	interface ObservedMethodRepository extends Repository<Example, Long> {

		@Observed(name = "repository.observed.method")
		Example findById(long id);

	}

	@Observed(name = "repository.observed.class")
	interface ObservedClassRepository extends Repository<Example, Long> {

		Example findById(long id);

	}

	private static class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

		private final List<String> observationNames = new CopyOnWriteArrayList<>();

		@Override
		public void onStart(Observation.Context context) {
			this.observationNames.add(context.getName());
		}

		@Override
		public boolean supportsContext(Observation.Context context) {
			return true;
		}

		List<String> observationNames() {
			return this.observationNames;
		}

	}

	static class Example {

	}

}
