package villagers;

/** Put on every Player by PlayerMixin: whether they have eaten the Heart of Darkness. */
public interface InhumanHolder {
	boolean vil$inhuman();
	void vil$setInhuman(boolean inhuman);
	/** Ticks since the heart was eaten while the change plays out, or -1. */
	int vil$rite();
	void vil$setRite(int ticks);
}
