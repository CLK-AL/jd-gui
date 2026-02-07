package al.clk.gui.util.map;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MapToolbox {

	public static <Source, Key extends Comparable<? super Key>, Value> LinkedHashMap<Key, Value> toLinkedHashMap(Function<Source, Key> sortComparator,
	                                                                                                             Function<Source, Key> keyMapper,
	                                                                                                             Function<Source, Value> valueMapper,
	                                                                                                             Source... sources) {
		LinkedHashMap<Key, Value> keyValueLinkedHashMap = Arrays.stream(sources)
		                                                        .sorted(Comparator.comparing(sortComparator))
		                                                        .collect(Collectors.toMap(keyMapper,
		                                                                                  valueMapper,
		                                                                                  (x, y) -> y,
		                                                                                  LinkedHashMap::new));
		return keyValueLinkedHashMap;
	}

	public static <Source, Key extends Comparable<? super Key>, Value> LinkedHashMap<Key, Value> toLinkedHashMap(Function<Source, Key> sortComparator,
	                                                                                                             Function<Source, Key> keyMapper,
	                                                                                                             Function<Source, Value> valueMapper,
	                                                                                                             Collection<Source> sources) {
		LinkedHashMap<Key, Value> keyValueLinkedHashMap = sources.stream()
		                                                         .sorted(Comparator.comparing(sortComparator))
		                                                         .collect(Collectors.toMap(keyMapper,
		                                                                                   valueMapper,
		                                                                                   (x, y) -> y,
		                                                                                   LinkedHashMap::new));
		return keyValueLinkedHashMap;
	}
}
