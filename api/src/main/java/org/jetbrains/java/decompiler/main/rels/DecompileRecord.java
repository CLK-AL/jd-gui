package org.jetbrains.java.decompiler.main.rels;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.DotExporter;

import java.util.ArrayList;
import java.util.List;

public final class DecompileRecord {
	private final List<String> names              = new ArrayList<>();
	private final StructMethod mt;
	private       int          mainLoopIteration  = 0;
	private       int          mergeLoopIteration = 0;

	public DecompileRecord(StructMethod mt) {
		this.mt = mt;
	}

	public void add(String name,
	                RootStatement root) {
		String exportName = "";
		if (this.mainLoopIteration > 0) {
			exportName += "Loop_" + this.mainLoopIteration + "_";
		}

		if (this.mergeLoopIteration > 0) {
			exportName += "Merge_" + this.mergeLoopIteration + "_";
		}

		exportName += name;

		add(exportName);

		DotExporter.toDotFile(root,
		                      mt,
		                      "debug",
		                      exportName);
	}

	public void add(String name) {
		this.names.add(name);
	}

	public void incrementMainLoop() {
		this.mainLoopIteration++;
	}

	public void incrementMergeLoop() {
		this.mergeLoopIteration++;
	}

	public void resetMainLoop() {
		this.mainLoopIteration = 0;
	}

	public void resetMergeLoop() {
		this.mergeLoopIteration = 0;
	}

	public List<String> getNames() {
		return names;
	}

	public void print() {
		IFernflowerLogger logger = DecompilerContext.getLogger();
		for (int i = 0;
		     i < this.names.size();
		     i++) {
			String message = i + " " + this.names.get(i);
			if (logger != null) {
				logger.writeMessage(message, IFernflowerLogger.Severity.INFO);
			}
		}
	}
}
