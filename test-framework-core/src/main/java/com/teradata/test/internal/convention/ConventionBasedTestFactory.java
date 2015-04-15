/*
 * Copyright 2013-2015, Teradata, Inc. All rights reserved.
 */

package com.teradata.test.internal.convention;

import com.google.common.collect.ImmutableList;
import com.teradata.test.internal.convention.generator.GeneratorPathTestFactory;
import com.teradata.test.internal.convention.recursion.RecursionPathTestFactory;
import com.teradata.test.internal.convention.sql.SqlPathTestFactory;
import org.slf4j.Logger;
import org.testng.annotations.Factory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.teradata.test.fulfillment.table.TableDefinitionsRepository.tableDefinitionsRepository;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

public class ConventionBasedTestFactory
{
    private static final Logger LOGGER = getLogger(ConventionBasedTestFactory.class);
    private static final ConventionBasedTest[] NO_TEST_CASES = new ConventionBasedTest[0];

    // TODO: make this configurable
    private static final String TEST_PACKAGE = "com.teradata.tests";
    public static final String TESTCASES_PATH_PART = "testcases";

    public interface PathTestFactory
    {

        boolean isSupportedPath(Path path);

        List<ConventionBasedTest> createTestsForPath(Path path, String testNamePrefix, ConventionBasedTestFactory factory);
    }

    private List<PathTestFactory> factories;

    @Factory
    public ConventionBasedTest[] createTestCases()
    {
        LOGGER.debug("Loading file based test cases");
        try {
            Optional<Path> productTestPath = ConventionTestsUtils.getConventionsTestsPath(TESTCASES_PATH_PART);
            if (!productTestPath.isPresent()) {
                return NO_TEST_CASES;
            }
            factories = setupFactories();
            return createTestsForRootPath(productTestPath.get()).toArray(new ConventionBasedTest[0]);
        }
        catch (Exception e) {
            LOGGER.error("Could not create file test", e);
            throw new RuntimeException("Could not create test cases", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<PathTestFactory> setupFactories()
    {
        return ImmutableList.of(
                new RecursionPathTestFactory(),
                new GeneratorPathTestFactory(),
                new SqlPathTestFactory(tableDefinitionsRepository(), new ConventionBasedTestProxyGenerator(TEST_PACKAGE)));
    }

    public List<ConventionBasedTest> createTestsForPath(Path path, String testNamePrefix)
    {
        return factories.stream()
                .filter(f -> f.isSupportedPath(path))
                .flatMap(f -> f.createTestsForPath(path, testNamePrefix, this).stream())
                .collect(toList());
    }

    public List<ConventionBasedTest> createTestsForChildrenOfPath(Path path, String testNamePrefix)
    {
        try {
            // TODO tree traversal for ZIP file system (when resources are inside jar) results with Exception
            // TODO https://hadapt.jira.com/browse/SWARM-246
            return Files.list(path)
                    .flatMap(child -> createTestsForPath(child, testNamePrefix).stream())
                    .collect(toList());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ConventionBasedTest> createTestsForRootPath(Path path)
    {
        return createTestsForPath(path, "sql_tests");
    }
}