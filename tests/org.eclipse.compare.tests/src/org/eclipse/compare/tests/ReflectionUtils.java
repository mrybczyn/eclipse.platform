/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.compare.tests;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtils {

	public static Object callMethod(Object object, String name, Object args[])
			throws IllegalArgumentException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException {
		Class types[] = new Class[args.length];
		for (int i = 0; i < args.length; i++) {
			types[i] = args[i].getClass();
		}
		Method method = null;
		Class clazz = object.getClass();
		NoSuchMethodException ex = null;
		while (method == null && clazz != null) {
			try {
				method = clazz.getDeclaredMethod(name, types);
			} catch (NoSuchMethodException e) {
				if (ex == null) {
					ex = e;
				}
				clazz = clazz.getSuperclass();
			}
		}
		if (method == null) {
			throw ex;
		}
		method.setAccessible(true);
		Object ret = method.invoke(object, args);
		return ret;
	}

	public static Object getField(Object object, String name)
			throws IllegalArgumentException, IllegalAccessException,
			SecurityException, NoSuchFieldException {
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		Object ret = field.get(object);
		return ret;
	}

}