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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.micrometer.observation.annotation.Observed;
import org.jspecify.annotations.Nullable;

import org.springframework.core.annotation.MergedAnnotationCollectors;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Utility used to obtain {@link Observed @Observed} annotations from bean methods.
 *
 * @since 4.0.0
 */
public final class ObservedAnnotations {

	private static final Map<AnnotatedElement, Set<Observed>> cache = new ConcurrentReferenceHashMap<>();

	private ObservedAnnotations() {
	}

	/**
	 * Return {@link Observed} annotations that should be used for the given
	 * {@code method} and {@code type}.
	 * @param method the source method
	 * @param type the source type
	 * @return the {@link Observed} annotations to use or an empty set
	 */
	public static Set<Observed> get(Method method, Class<?> type) {
		Set<Observed> methodAnnotations = findObservedAnnotations(method);
		if (!methodAnnotations.isEmpty()) {
			return methodAnnotations;
		}
		return findObservedAnnotations(type);
	}

	private static Set<Observed> findObservedAnnotations(@Nullable AnnotatedElement element) {
		if (element == null) {
			return Collections.emptySet();
		}
		Set<Observed> result = cache.get(element);
		if (result != null) {
			return result;
		}
		MergedAnnotations annotations = MergedAnnotations.from(element);
		result = (!annotations.isPresent(Observed.class)) ? Collections.emptySet()
				: annotations.stream(Observed.class).collect(MergedAnnotationCollectors.toAnnotationSet());
		cache.put(element, result);
		return result;
	}

}
