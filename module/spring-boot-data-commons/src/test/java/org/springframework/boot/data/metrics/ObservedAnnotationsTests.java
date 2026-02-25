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
import java.util.Set;

import io.micrometer.observation.annotation.Observed;
import org.junit.jupiter.api.Test;

import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObservedAnnotations}.
 */
class ObservedAnnotationsTests {

	@Test
	void getWhenNoneReturnsEmptySet() {
		Object bean = new None();
		Method method = ReflectionUtils.findMethod(bean.getClass(), "handle");
		assertThat(method).isNotNull();
		Set<Observed> annotations = ObservedAnnotations.get(method, bean.getClass());
		assertThat(annotations).isEmpty();
	}

	@Test
	void getWhenOnMethodReturnsMethodAnnotation() {
		Object bean = new OnMethod();
		Method method = ReflectionUtils.findMethod(bean.getClass(), "handle");
		assertThat(method).isNotNull();
		Set<Observed> annotations = ObservedAnnotations.get(method, bean.getClass());
		assertThat(annotations).singleElement().extracting(Observed::name).isEqualTo("method.observed");
	}

	@Test
	void getWhenNotOnMethodReturnsBeanAnnotation() {
		Object bean = new OnBean();
		Method method = ReflectionUtils.findMethod(bean.getClass(), "handle");
		assertThat(method).isNotNull();
		Set<Observed> annotations = ObservedAnnotations.get(method, bean.getClass());
		assertThat(annotations).singleElement().extracting(Observed::name).isEqualTo("bean.observed");
	}

	static class None {

		void handle() {
		}

	}

	static class OnMethod {

		@Observed(name = "method.observed")
		void handle() {
		}

	}

	@Observed(name = "bean.observed")
	static class OnBean {

		void handle() {
		}

	}

}
