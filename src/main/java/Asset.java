public class Asset {
    private String id;
    private String name;
    private String base;

    public Asset() {}

    public Asset(String id, String name, String base) {
        this.id = id;
        this.name = name;
        this.base = base;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    @Override
    public String toString() {
        return id + " - " + name + " - " + base;
    }
}
