package calculator;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.utils.SourceRoot;
import infrastructure.entities.JavaClass;
import infrastructure.entities.JavaFile;
import infrastructure.entities.Project;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import visitors.ClassVisitor;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MetricsCalculator {

    @Getter
    private final Project project;
    private AtomicInteger fileAnalysisProgressPercentage;

    private static final Logger logger = LogManager.getLogger(MetricsCalculator.class);

    public MetricsCalculator(Project project) {
        this.project = project;
        this.fileAnalysisProgressPercentage = new AtomicInteger(1);
    }

    /**
     * Start the whole process
     *
     * @return 0 if everything went ok, -1 otherwise
     */
    public int start() {
        ParserConfiguration parserConfiguration;
        try {
            parserConfiguration = createSymbolSolver(project.getClonePath());
        } catch (IllegalStateException e) {
            return -1;
        }
        ProjectRoot projectRoot = getProjectRoot(project.getClonePath(), parserConfiguration);
        List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
        if (createFileSet(sourceRoots) == 0) {
            logger.error("No classes could be identified! Exiting...");
            return -1;
        }
        startCalculations(sourceRoots);
        performAggregation();
        return 0;
    }

    /**
     * Aggregates quality metrics
     */
    private void performAggregation() {
        project.getJavaFiles().forEach(JavaFile::aggregateMetrics);
    }

    /**
     * Get the project root
     */
    private ProjectRoot getProjectRoot(String projectDir, ParserConfiguration parserConfiguration) {
        logger.info("Collecting source roots...");
        return new SymbolSolverCollectionStrategy(parserConfiguration)
                .collect(Paths.get(projectDir));
    }

    /**
     * Create the symbol solver
     * that will be used to identify
     * user-defined classes
     */
    private static ParserConfiguration createSymbolSolver(String projectDir) {

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver))
                .setAttributeComments(false).setDetectOriginalLineSeparator(true)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

        TypeSolver javaParserTypeSolver = new JavaParserTypeSolver(new File(projectDir), parserConfiguration);
        combinedTypeSolver.add(new ReflectionTypeSolver(false));
        combinedTypeSolver.add(javaParserTypeSolver);

        StaticJavaParser.setConfiguration(parserConfiguration);
        return parserConfiguration;
    }

    /**
     * Creates the file set (add appropriate classes)
     *
     * @param sourceRoots the source roots of project
     * @return size of the file set (int)
     */
    private int createFileSet(List<SourceRoot> sourceRoots) {
        try {
            sourceRoots
                    .forEach(sourceRoot -> {
                        try {
                            sourceRoot.tryToParse()
                                    .stream()
                                    .filter(res -> res.getResult().isPresent())
                                    .filter(cu -> cu.getResult().get().getStorage().isPresent())
                                    .filter(cu -> !cu.getResult().get().getStorage().get().getPath().toString().contains("Test"))
                                    .forEach(cu -> {
                                        try {
                                            project.getJavaFiles().add(new JavaFile(cu.getResult().get().getStorage().get().getPath().toString().replace(project.getClonePath(), "").substring(1),
                                                    cu.getResult().get().findAll(ClassOrInterfaceDeclaration.class)
                                                            .stream()
                                                            .filter(classOrInterfaceDeclaration -> classOrInterfaceDeclaration.getFullyQualifiedName().isPresent())
                                                            .map(classOrInterfaceDeclaration -> classOrInterfaceDeclaration.getFullyQualifiedName().get())
                                                            .map(JavaClass::new)
                                                            .collect(Collectors.toSet())));
                                        } catch (Throwable ignored) {
                                        }
                                    });
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
        return project.getJavaFiles().size();
    }

    /**
     * Starts the calculations
     *
     * @param sourceRoots the list of source roots of project
     */
    private void startCalculations(List<SourceRoot> sourceRoots) {
        AtomicInteger srcRootProgress = new AtomicInteger(1);
        AtomicInteger overallFileAnalysisProgress = new AtomicInteger(1);
        sourceRoots
                .forEach(sourceRoot -> {
                    logger.info("Analysing Source Root: {} ({}/{})...", sourceRoot.getRoot().toString(), srcRootProgress, sourceRoots.size());
                    AtomicInteger fileAnalysisProgress = new AtomicInteger(1);
                    try {
                        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();
                        parseResults
                                .stream()
                                .filter(res -> res.getResult().isPresent())
                                .forEach(res -> {
                                    logger.info("\rAnalysing Source Root: {} ({}%) ({}/{})...", sourceRoot.getRoot().toString(), fileAnalysisProgress.getAndIncrement() * 100 / parseResults.size(), srcRootProgress, sourceRoots.size());
                                    analyzeCompilationUnit(res.getResult().get());
                                    int percentage = overallFileAnalysisProgress.getAndIncrement()*100/getProject().getJavaFiles().size();
                                    setOverallProgress(new AtomicInteger(percentage != 0 ? percentage : 1));
                                });
                    } catch (Exception ignored) {
                    }
                    srcRootProgress.getAndIncrement();
                });
    }

    /**
     * Analyzes the compilation unit given.
     *
     * @param cu the compilation unit given
     */
    private void analyzeCompilationUnit(CompilationUnit cu) {
        analyzeClassOrInterfaces(cu);
        analyzeEnums(cu);
    }

    /**
     * Analyzes the classes (or interfaces) given a compilation unit.
     *
     * @param cu the compilation unit given
     */
    private void analyzeClassOrInterfaces(CompilationUnit cu) {
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cl -> {
            try {
                cl.accept(new ClassVisitor(project.getJavaFiles(), cu.getStorage().get().getPath().toString().replace(project.getClonePath(), "").substring(1), cl), null);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Analyzes the enumerations given a compilation unit.
     *
     * @param cu the compilation unit given
     */
    private void analyzeEnums(CompilationUnit cu) {
        cu.findAll(EnumDeclaration.class).forEach(cl -> {
            try {
                cl.accept(new ClassVisitor(project.getJavaFiles(), cu.getStorage().get().getPath().toString().replace(project.getClonePath(), "").substring(1), cl), null);
            } catch (Exception ignored) {
            }
        });
    }

    public AtomicInteger getOverallProgress() {
        return fileAnalysisProgressPercentage;
    }

    public void setOverallProgress(AtomicInteger fileAnalysisProgressPercentage) {
        this.fileAnalysisProgressPercentage = fileAnalysisProgressPercentage;
    }
}
