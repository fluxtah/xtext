package org.eclipse.xtend.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.xtext.generator.trace.AbstractTraceRegion;
import org.eclipse.xtext.generator.trace.ITraceToBytecodeInstaller;
import org.eclipse.xtext.generator.trace.TraceAsPrimarySourceInstaller;
import org.eclipse.xtext.generator.trace.TraceAsSmapInstaller;
import org.eclipse.xtext.generator.trace.TraceFileNameProvider;
import org.eclipse.xtext.generator.trace.TraceRegionSerializer;
import org.eclipse.xtext.util.Strings;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * @author Moritz Eysholdt - Initial contribution and API
 */
public abstract class AbstractXtendInstallDebugInfoMojo extends AbstractMojo {

	@Inject
	protected ClassFileDebugSourceExtractor classFileDebugSourceExtractor;

	/**
	 * Set this to false to show synthetic variables in the debugger. This only has an effect if
	 * {@link #traceAsPrimarySourceInstallerProvider} is set to true. Synthetic variables are variables which are
	 * created by the Xtend compiler. Therefore they only exist in the compiled Java code but not in the Xtend code.
	 * 
	 * @parameter default-value="true" expression="${hideSyntheticVariables}"
	 */
	protected boolean hideSyntheticVariables;

	/**
	 * The project itself. This parameter is set by maven.
	 * 
	 * @parameter expression="${project}"
	 * @required
	 */
	protected MavenProject project;

	@Inject
	private Provider<TraceAsPrimarySourceInstaller> traceAsPrimarySourceInstallerProvider;

	@Inject
	private Provider<TraceAsSmapInstaller> traceAsSmapInstaller;

	@Inject
	private TraceFileNameProvider traceFileNameProvider;

	/**
	 * Set this to true to use the Xtend sources as the primary debugging sources. This will completely hide the Java
	 * sources in the debugger. You'll need to enable it if your JVM doesn't support JSR-045.
	 * 
	 * @parameter default-value="false" expression="${xtendAsPrimaryDebugSource}"
	 */
	protected boolean xtendAsPrimaryDebugSource;

	public AbstractXtendInstallDebugInfoMojo() {
		new XtendMavenStandaloneSetup().createInjectorAndDoEMFRegistration().injectMembers(this);
	}

	protected void collectJavaSourceFile2traceFile(String root, String subdir,
			Map<String, File> javaSourceFile2traceFile) {
		for (File child : new File(root + "/" + subdir).listFiles()) {
			String name = child.getName();
			if (child.isDirectory())
				collectJavaSourceFile2traceFile(root, subdir + "/" + name, javaSourceFile2traceFile);
			else if (name.endsWith(TraceFileNameProvider.TRACE_FILE_EXTENSION)) {
				String javaSourceFile = subdir + "/" + traceFileNameProvider.getJavaFromTrace(name);
				javaSourceFile2traceFile.put(javaSourceFile, child);
			}
		}
	}

	protected ITraceToBytecodeInstaller createTraceToBytecodeInstaller() {
		if (xtendAsPrimaryDebugSource) {
			TraceAsPrimarySourceInstaller installer = traceAsPrimarySourceInstallerProvider.get();
			installer.setHideSyntheticVariables(hideSyntheticVariables);
			return installer;
		} else {
			TraceAsSmapInstaller installer = traceAsSmapInstaller.get();
			return installer;
		}
	}

	@Inject
	protected TraceRegionSerializer traceRegionSerializer;

	public void execute() throws MojoExecutionException, MojoFailureException {
		internalExecute();
	}

	protected abstract void internalExecute();

	protected void installTraces(Multimap<File, File> trace2class) {
		for (Map.Entry<File, Collection<File>> e : trace2class.asMap().entrySet()) {
			try {
				installTrace(e.getKey(), e.getValue());
			} catch (Exception e1) {
				getLog().error(e1);
			}
		}
	}

	protected void logStatus(String folder, Multimap<File, File> trace2class) {
		String p = xtendAsPrimaryDebugSource ? "primary" : "secondary (via SMAP)";
		int n = trace2class.size();
		getLog().info("Installing Xtend files into " + n + " class files as " + p + " debug sources in: " + folder);
	}

	protected void installTrace(File traceFile, Collection<File> classFiles) throws FileNotFoundException, IOException {
		ITraceToBytecodeInstaller traceToBytecodeInstaller = createTraceToBytecodeInstaller();
		InputStream in = new FileInputStream(traceFile);
		try {
			AbstractTraceRegion traceRegion = traceRegionSerializer.readTraceRegionFrom(in);
			traceToBytecodeInstaller.setTrace(traceFileNameProvider.getJavaFromTrace(traceFile.getName()), traceRegion);
			for (File classFile : classFiles)
				Files.write(traceToBytecodeInstaller.installTrace(Files.toByteArray(classFile)), classFile);
		} finally {
			in.close();
		}
	}

	protected Multimap<File, File> createTraceToClassFileMap(List<String> sourceFolders, String outputFolder) {
		Map<String, File> javaSourceFile2traceFile = Maps.newLinkedHashMap();

		for (String sourceRoot : sourceFolders)
			collectJavaSourceFile2traceFile(sourceRoot, "", javaSourceFile2traceFile);

		Set<String> packageDirs = Sets.newLinkedHashSet();
		for (String javaSourceFile : javaSourceFile2traceFile.keySet())
			packageDirs.add(Strings.skipLastToken(javaSourceFile, "/"));

		Multimap<File, File> trace2class = LinkedHashMultimap.create();

		for (String packageDirName : packageDirs) {
			File packageDir = new File(outputFolder + "/" + packageDirName);
			if (packageDir.isDirectory()) {
				for (File classFile : packageDir.listFiles())
					if (classFile.getName().endsWith(".class"))
						try {
							String sourceFileName = classFileDebugSourceExtractor.getDebugSourceFileName(classFile);
							if (Strings.isEmpty(sourceFileName))
								continue;
							if (!sourceFileName.toLowerCase().endsWith(".java"))
								continue;
							File traceFile = javaSourceFile2traceFile.get(packageDirName + "/" + sourceFileName);
							if (traceFile != null)
								trace2class.put(traceFile, classFile);
						} catch (IOException e) {
							getLog().error("Error reading " + classFile, e);
						}
			}
		}
		return trace2class;
	}
}