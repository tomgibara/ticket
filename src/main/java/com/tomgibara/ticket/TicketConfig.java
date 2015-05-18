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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * Configures a {@link TicketFactory}. Instances of this class provide all the
 * information needed to create interoperable tickets; ticket factories with
 * equal configurations will produce interchangeable tickets (with one caveat,
 * see below).
 * <p>
 * Configuration may be serialized and shared between processes for the purpose
 * of creating system-wide compatible tickets.
 * <p>
 * For configurations that that include the hashing of tickets, it is possible
 * that one or more secrets may have been supplied at the time that a
 * {@link TicketFactory} was constructed from the configuration. These secrets
 * are naturally required to authenticate tickets, but are <em>not</em> recorded
 * in instances of this class for security reasons.
 * <p>
 * Note that this configuration does not control the formatting of tickets which
 * is controlled with {@link TicketFactory#setFormat(TicketFormat)}.
 * <p>
 * Instances of this class are safe for concurrent access by multiple threads.
 *
 * @author Tom Gibara
 *
 * @param <R>
 *            the type of origin information to be recorded in tickets
 * @param <D>
 *            the type of data information to be recorded in tickets
 * @see TicketSpec
 */

public final class TicketConfig<R,D> implements Serializable {

	private static final long serialVersionUID = -4316520322538523004L;

	// statics constants

	private static final List<TicketSpec> DEFAULT_SPECS = Collections.singletonList( TicketSpec.getDefault() );
	private static final TicketAdapter<Void> DEFAULT_ADAPTER = TicketAdapter.newData(Void.class);
	private static final TicketConfig<Void,Void> DEFAULT = new TicketConfig<Void, Void>(DEFAULT_ADAPTER, DEFAULT_ADAPTER, DEFAULT_SPECS);

	// static methods

	/**
	 * Default ticket factory configuration. Using this configuration, tickets
	 * will be created using the default specification, without origin data or
	 * ticket data.
	 *
	 * @return the default factory configuration
	 * @see TicketSpec#getDefault()
	 */

	public static TicketConfig<Void,Void> getDefault() {
		return DEFAULT;
	}

	// static helper methods

	private static List<TicketSpec> checkedSpecs(TicketSpec[] specs) {
		if (specs == null || specs.length == 0) return DEFAULT_SPECS;
		List<TicketSpec> list = Arrays.asList(specs.clone());
		if (list.contains(null)) throw new IllegalArgumentException("null spec");
		return Collections.unmodifiableList(list);
	}

	// fields

	final TicketAdapter<R> originAdapter;
	final TicketAdapter<D> dataAdapter;
	final List<TicketSpec> specifications;


	// constructors

	private TicketConfig(TicketAdapter<R> originAdapter, TicketAdapter<D> dataAdapter, List<TicketSpec> specs) {
		this.originAdapter = originAdapter;
		this.dataAdapter = dataAdapter;
		this.specifications = specs;
	}

	// accessors

	/**
	 * The type of origin data to be recorded in tickets.
	 *
	 * @return an interface class or <code>Void.class</code>.
	 */

	public Class<? extends R> getOriginType() {
		return originAdapter.getType();
	}

	/**
	 * The type of ticket data to be recorded in tickets.
	 *
	 * @return an interface class or <code>Void.class</code>.
	 */

	public Class<? extends D> getDataType() {
		return dataAdapter.getType();
	}

	/**
	 * The specifications by which tickets will be decoded or constructed.
	 *
	 * @return a list of ticket specifications
	 */

	public List<TicketSpec> getSpecifications() {
		return specifications;
	}

	// methods

	/**
	 * <p>
	 * Returns a configuration which is identical to the present configuration
	 * but for the specified type of origin data.
	 * <p>
	 * The parameter supplied to this method must be a valid ticket data class.
	 *
	 * @param originType
	 *            <code>Void.class</code> or an interface
	 * @return a new configuration or possibly the same
	 * @see TicketField
	 */

	//TODO should return same instance if type is the same
	public <S> TicketConfig<S,D> withOriginType(Class<? extends S> originType) {
		if (originType == null) throw new IllegalArgumentException("null originType");
		return new TicketConfig<S, D>(TicketAdapter.newData(originType), dataAdapter, specifications);
	}

	/**
	 * <p>
	 * Returns a configuration which is identical to the present configuration
	 * but for the specified type of ticket data.
	 * <p>
	 * The parameter supplied to this method must be a valid ticket data class.
	 *
	 * @param dataType
	 *            <code>Void.class</code> or an interface
	 * @return a new configuration or possibly the same
	 * @see TicketField
	 */

	//TODO should return same instance if type is the same
	public <E> TicketConfig<R,E> withDataType(Class<? extends E> dataType) {
		if (dataType == null) throw new IllegalArgumentException("null dataType");
		return new TicketConfig<R,E>(originAdapter, TicketAdapter.newData(dataType), specifications);
	}

	/**
	 * <p>
	 * Defines the specifications under which tickets will be created and
	 * decoded.
	 * <p>
	 * Many configurations will only specify a single specification, but in
	 * systems where the ticket specification has changed in incompatible ways,
	 * it will frequently be necessary for a ticket factory to decode tickets
	 * created under 'old' specifications while still producing tickets using
	 * the latest specification. In this way, the specs parameter should be form
	 * a precisely ordered list of all previous specifications, with the current
	 * specification last.
	 * <p>
	 * If a null or empty array is supplied to the method, then the default
	 * specification is used.
	 *
	 * @param specs
	 *            the ticket specifications known to the system, possibly null
	 *            or empty.
	 * @return a new configuration with using the indicated specifications
	 * @see TicketSpec#getDefault()
	 */

	public TicketConfig<R,D> withSpecifications(TicketSpec... specs) {
		return new TicketConfig<R,D>(originAdapter, dataAdapter, checkedSpecs(specs));
	}

	/**
	 * <p>
	 * Creates a new ticket factory with this configuration.
	 * <p>
	 * The secrets parameter is optional and is only effective when combined
	 * with specifications which include ticket hashing. Secret byte arrays
	 * (which remain secret) can be used to create tickets that (with a high
	 * probability) cannot be substituted by externally modified alternatives.
	 * <p>
	 * When supplied, the number of secrets must not exceed the number of
	 * specifications. The secret at the <em>nth</em> index is used in
	 * combination with the <em>nth</em> specification. If the number of secrets
	 * is less than the number of specifications in the configuration then the
	 * last declared secret will be used for all subsequent specifications. In
	 * all other cases, an absent or null, or zero length secret will be treated
	 * identically.
	 *
	 * @param secrets
	 *            the secrets used to create unfalsifiable tickets
	 * @return a new ticket factory
	 */
	public TicketFactory<R,D> newFactory(byte[]... secrets) {
		return new TicketFactory<R, D>(this, secrets);
	}

	// object methods

	@Override
	public int hashCode() {
		return specifications.hashCode() + originAdapter.hashCode() + 31 * dataAdapter.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof TicketConfig)) return false;
		TicketConfig<?, ?> that = (TicketConfig<?, ?>) obj;
		if (!this.originAdapter.equals(that.originAdapter)) return false;
		if (!this.dataAdapter.equals(that.dataAdapter)) return false;
		if (!this.specifications.equals(that.specifications)) return false;
		return true;
	}

	public String toString() {
		return String.format(
				"originAdapter: %s, dataAdapter: %s, specifications: %s",
				originAdapter, dataAdapter, specifications
				);
	}
}
