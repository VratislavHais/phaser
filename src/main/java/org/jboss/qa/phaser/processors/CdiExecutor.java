/*
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.qa.phaser.processors;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class CdiExecutor {

	private FieldProcessor defaultProcessor;

	@Singular
	private List<FieldProcessor> processors;

	public void inject(Object bean) {

		Class<?> current = bean.getClass();
		while (current.getSuperclass() != null) {
			for (final Field field : current.getDeclaredFields()) {
				try {
					field.setAccessible(true);
					Object value = field.get(bean);
					if (field.getAnnotations().length > 0) {
						for (FieldProcessor processor : processors) {
							final Annotation annotation = field.getAnnotation(processor.getAnnotationClass());
							if (annotation != null) {
								if (value != null) {
									value = processor.processField(field.getType(), annotation, value);
								} else {
									value = processor.processField(field.getType(), annotation);
								}
								field.set(bean, value);
							}
						}
					} else if (defaultProcessor != null) {
						if (value != null) {
							value = defaultProcessor.processField(field.getType(), null, value);
						} else {
							value = defaultProcessor.processField(field.getType(), null);
						}
						field.set(bean, value);
					}
				} catch (Exception e) {
					throw new RuntimeException("Can not inject value into field `" + field.getName() + "` in class " + bean.getClass().getCanonicalName(), e);
				}
			}
			current = current.getSuperclass();
		}
	}
}

