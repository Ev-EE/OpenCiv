package me.rhin.openciv.shared.stat;

public enum Stat {
	GOLD, 
	GOLD_GAIN(GOLD),
	MAINTENANCE(GOLD),
	HERITAGE,
	HERITAGE_GAIN(HERITAGE),
	RESEARCH_GAIN,
	PRODUCTION_GAIN,
	FOOD_SURPLUS,
	FOOD_GAIN,
	POPULATION,
	EXPANSION_REQUIREMENT,
	POLICY_COST;

	private Stat addedStat;

	private Stat() {
		// An empty enum means the stat is APPLIED to whatever is applicable to the
		// resource.
	}

	private Stat(Stat addedStat) {
		this.addedStat = addedStat;
		// A non-empty enum means the stat is ACCUMULATED to whatever is applicable to
		// the resource.
	}

	public boolean isGained() {
		return addedStat != null;
	}

	public Stat getAddedStat() {
		return addedStat;
	}
}
