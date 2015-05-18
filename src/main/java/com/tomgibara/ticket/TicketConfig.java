package com.tomgibara.ticket;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TicketConfig<R,D> implements Serializable {

	private static final long serialVersionUID = -4316520322538523004L;

	// statics constants

	private static final List<TicketSpec> DEFAULT_SPECS = Collections.singletonList( TicketSpec.getDefault() );
	private static final TicketAdapter<Void> DEFAULT_ADAPTER = TicketAdapter.newData(Void.class);
	private static final TicketConfig<Void,Void> DEFAULT = new TicketConfig<Void, Void>(DEFAULT_ADAPTER, DEFAULT_ADAPTER, DEFAULT_SPECS);

	// static methods

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

	public Class<? extends R> getOriginType() {
		return originAdapter.getType();
	}

	public Class<? extends D> getDataType() {
		return dataAdapter.getType();
	}

	public List<TicketSpec> getSpecifications() {
		return specifications;
	}

	// methods

	public <S> TicketConfig<S,D> withOriginType(Class<? extends S> originType) {
		if (originType == null) throw new IllegalArgumentException("null originType");
		return new TicketConfig<S, D>(TicketAdapter.newData(originType), dataAdapter, specifications);
	}

	public <E> TicketConfig<R,E> withDataType(Class<? extends E> dataType) {
		if (dataType == null) throw new IllegalArgumentException("null dataType");
		return new TicketConfig<R,E>(originAdapter, TicketAdapter.newData(dataType), specifications);
	}

	public TicketConfig<R,D> withSpecifications(TicketSpec... specs) {
		return new TicketConfig<R,D>(originAdapter, dataAdapter, checkedSpecs(specs));
	}

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
