/*
 * Copyright 2015 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.tomgibara.ticket;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Defines a field on a type interface. The annotation is used to serialize
 * values into tickets and to expose these values to Java code.
 * <p>
 * Additional ticket information is supplied via interfaces which declare
 * accessors that have been annotated by TicketField.
 * <ul>
 * <li>The interface must have at least one annotated accessor.
 * <li>Every accessors must return: a primitive, an array of primitives, a
 * <code>String</code> or an </code>Enum</code>.
 * <li>Accessor methods cannot have arguments.
 * <li>Every accessor must be assigned a unique index (see {@link #value()}).
 * <li>The indices must begin at 0 and be contiguous.
 * <li>The accessors must be declared directly by the interface (ie. not
 * inherited).
 * </ul>
 * <p>
 * An interface which meets these requirements constitutes a valid ticket data
 * interface.
 * </p>
 * Note that if an interface which has already been used in a
 * {@link TicketConfig} is modified to add new ticket fields, the corresponding
 * accessors must be annotated with index values starting with the least unused
 * index. Otherwise, one of the existing fields must inevitably have changed its
 * definition, with the result that previously valid tickets may become
 * unreadable.
 *
 * @author Tom Gibara
 * @see TicketFactory#machineForOriginValues(Object...)
 * @see TicketMachine#ticketDataValues(Object...)
 *
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TicketField {

	/**
	 * The index of the field in the ticket data encoding.
	 *
	 * @return
	 */

	int value();

}
