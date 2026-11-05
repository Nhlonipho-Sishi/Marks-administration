package model;

/** One test/assessment row for a module, carrying its contribution weight. */
public class Assessment {
    private final int id;
    private final String name;
    private final double maxMarks;
    private final double weightPct;

    public Assessment(int id, String name, double maxMarks, double weightPct) {
        this.id = id; this.name = name; this.maxMarks = maxMarks; this.weightPct = weightPct;
    }
    public int getId()           { return id; }
    public String getName()      { return name; }
    public double getMaxMarks()  { return maxMarks; }
    public double getWeightPct() { return weightPct; }

    /** Weighted contribution of a raw mark - core of the aggregation algorithm. */
    public double weighted(double mark) { return (mark / maxMarks) * weightPct; }
}
