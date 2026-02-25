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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.jspecify.annotations.Nullable;

import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocation;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocationResult;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

/**
 * Intercepts Spring Data {@code Repository} invocations and records metrics about
 * execution time and results. If an invocation is {@link Observed @Observed}, an
 * {@link Observation} is also created.
 *
 * @author Phillip Webb
 * @since 4.0.0
 */
public class MetricsRepositoryMethodInvocationListener implements RepositoryMethodInvocationListener {

	private final SingletonSupplier<MeterRegistry> registrySupplier;

	private final RepositoryTagsProvider tagsProvider;

	private final String metricName;

	private final AutoTimer autoTimer;

	private final @Nullable SingletonSupplier<ObservationRegistry> observationRegistrySupplier;

	/**
	 * Create a new {@code MetricsRepositoryMethodInvocationListener}.
	 * @param registrySupplier a supplier for the registry to which metrics are recorded
	 * @param tagsProvider provider for metrics tags
	 * @param metricName name of the metric to record
	 * @param autoTimer the auto-timers to apply or {@code null} to disable auto-timing
	 * @since 4.0.0
	 */
	public MetricsRepositoryMethodInvocationListener(Supplier<MeterRegistry> registrySupplier,
			RepositoryTagsProvider tagsProvider, String metricName, @Nullable AutoTimer autoTimer) {
		this(registrySupplier, tagsProvider, metricName, autoTimer, null);
	}

	/**
	 * Create a new {@code MetricsRepositoryMethodInvocationListener}.
	 * @param registrySupplier a supplier for the registry to which metrics are recorded
	 * @param tagsProvider provider for metrics tags
	 * @param metricName name of the metric to record
	 * @param autoTimer the auto-timers to apply or {@code null} to disable auto-timing
	 * @param observationRegistrySupplier a supplier for the observation registry or
	 * {@code null} if observations should not be created
	 */
	public MetricsRepositoryMethodInvocationListener(Supplier<MeterRegistry> registrySupplier,
			RepositoryTagsProvider tagsProvider, String metricName, @Nullable AutoTimer autoTimer,
			@Nullable Supplier<ObservationRegistry> observationRegistrySupplier) {
		this.registrySupplier = (registrySupplier instanceof SingletonSupplier)
				? (SingletonSupplier<MeterRegistry>) registrySupplier : SingletonSupplier.of(registrySupplier);
		this.tagsProvider = tagsProvider;
		this.metricName = metricName;
		this.autoTimer = (autoTimer != null) ? autoTimer : AutoTimer.DISABLED;
		this.observationRegistrySupplier = (observationRegistrySupplier instanceof SingletonSupplier)
				? (SingletonSupplier<ObservationRegistry>) observationRegistrySupplier
				: (observationRegistrySupplier != null) ? SingletonSupplier.of(observationRegistrySupplier) : null;
	}

	@Override
	public void afterInvocation(RepositoryMethodInvocation invocation) {
		Set<Timed> annotations = TimedAnnotations.get(invocation.getMethod(), invocation.getRepositoryInterface());
		List<Tag> tags = new ArrayList<>();
		this.tagsProvider.repositoryTags(invocation).forEach(tags::add);
		long duration = invocation.getDuration(TimeUnit.NANOSECONDS);
		AutoTimer.apply(this.autoTimer, this.metricName, annotations, (builder) -> {
			MeterRegistry registry = this.registrySupplier.get();
			Assert.state(registry != null, "'registry' must not be null");
			builder.description("Duration of repository invocations")
				.tags(tags)
				.register(registry)
				.record(duration, TimeUnit.NANOSECONDS);
		});
		recordObservation(invocation, tags);
	}

	private void recordObservation(RepositoryMethodInvocation invocation, List<Tag> tags) {
		Set<Observed> annotations = ObservedAnnotations.get(invocation.getMethod(),
				invocation.getRepositoryInterface());
		if (annotations.isEmpty()) {
			return;
		}
		ObservationRegistry observationRegistry = getObservationRegistry();
		if (observationRegistry == null) {
			return;
		}
		for (Observed annotation : annotations) {
			Observation observation = createObservation(invocation, tags, annotation, observationRegistry).start();
			RepositoryMethodInvocationResult result = invocation.getResult();
			if (result != null && result.getError() != null) {
				observation.error(result.getError());
			}
			observation.stop();
		}
	}

	private Observation createObservation(RepositoryMethodInvocation invocation, List<Tag> tags, Observed annotation,
			ObservationRegistry observationRegistry) {
		String observationName = (StringUtils.hasText(annotation.name())) ? annotation.name() : "method.observed";
		String contextualName = (StringUtils.hasText(annotation.contextualName())) ? annotation.contextualName()
				: invocation.getRepositoryInterface().getSimpleName() + "#" + invocation.getMethod().getName();
		Observation observation = Observation.createNotStarted(observationName, observationRegistry)
			.contextualName(contextualName)
			.lowCardinalityKeyValue("class", invocation.getRepositoryInterface().getName())
			.lowCardinalityKeyValue("method", invocation.getMethod().getName());
		for (Tag tag : tags) {
			observation.lowCardinalityKeyValue(tag.getKey(), tag.getValue());
		}
		for (KeyValue keyValue : KeyValues.of(annotation.lowCardinalityKeyValues())) {
			observation.lowCardinalityKeyValue(keyValue);
		}
		return observation;
	}

	private @Nullable ObservationRegistry getObservationRegistry() {
		return (this.observationRegistrySupplier != null) ? this.observationRegistrySupplier.get() : null;
	}

}
