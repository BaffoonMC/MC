package creativetabs;

/** Put on the creative inventory menu: which tab it is showing. */
public interface TabHolder {
	int ct$tab();
	/** Switch tab and rebuild the pages from it (and from whatever is typed in the search box). */
	void ct$applyTab(int tab);
}
