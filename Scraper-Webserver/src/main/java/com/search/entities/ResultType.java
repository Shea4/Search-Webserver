package com.search.entities;

public enum ResultType {

	RESULT(0),
	WEATHER(1), // async loaded, daily data is guaranteed however the data at the current time will sometimes be missing
	DEFINITION(2),
	ANSWER(3),
	CONVERSION(4),
	DATE_TIME(5),
	CALCULATOR(6),
	TRANSLATE(7),
	RANDOM_NUMBER(8),
	DIRECTIONS(9),
	FLIGHTS(10);

	private final int id;

	private ResultType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static ResultType fromId(int id) {
		for (ResultType type : ResultType.values()) {
			if (id == type.getId()) {
				return type;
			}
		}

		return null;
	}

}
