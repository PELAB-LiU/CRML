package crml.compiler.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class CSVListener implements TestExecutionListener, AfterEachCallback  {
  private static final CsvLogger logger = new CsvLogger("build"+ java.io.File.separator+ "files.csv", 
      List.<String>of("Test", "Index", "Name", "Pass", "Element", "File"));

  private static final Map<TestIdentifier, TestExecutionResult> RESULTS = new HashMap<>();
  private static final Map<TestIdentifier, String> SKIPPED = new HashMap<>();
  private static final Map<String, Map<String,? extends Object>> SHARED = new HashMap<>();

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    testPlan.getChildren(getRoot(testPlan)).forEach(testIdentifier -> {
      RESULTS.put(testIdentifier, null);
    }
  );
  
}
  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    testPlan.getChildren(getRoot(testPlan)).forEach(klass -> {
      if (RESULTS.containsKey(klass)) {
        testPlan.getDescendants(klass).forEach(test -> processTestNode(getKlassName(klass.getUniqueId()), test));
      }
    });
  }

  private void processTestNode(String clazz, TestIdentifier test) {
    if(test.isContainer())
            return;

    // remove the parametrized test case signature 
    if(test.getDisplayName().equals("simulateTestFile(String)"))
      return;
    
    logger.set("Test", clazz);

    String name = "";
    Pattern pattern_idx = Pattern.compile("^(\\[\\d+\\])");
    Matcher matcher_idx = pattern_idx.matcher(test.getDisplayName());
    if(matcher_idx.find()) {
      logger.set("Index", matcher_idx.group(1));
      name += matcher_idx.group(1)+" ";
    }
    Pattern pattern_path = Pattern.compile("((?:[ A-Za-z0-9_@.#&+-]+[\\\\\\/])?[^\\\\\\/]+)$");
    Matcher matcher_path = pattern_path.matcher(test.getDisplayName());
    if(matcher_path.find()) {//Find must be called.
      name += matcher_path.group(1);
    }
    
    logger.set("Name", name);
    Object file = SHARED.getOrDefault(test.getDisplayName(), new HashMap<String, Object>()).get("CRML File");
    logger.set("File", Objects.toString(file));
  
    final TestExecutionResult testResult = RESULTS.get(test);
    logger.set("Pass", testResult.getStatus().toString());
    
    try {
      logger.commit();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        SKIPPED.put(testIdentifier, reason);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        RESULTS.put(testIdentifier, testExecutionResult);
    }

    private String getKlassName(String uniqueId) {
        return getKlassName(UniqueId.parse(uniqueId));
    }

    private String getKlassName(UniqueId uniqueId) {
        return uniqueId
                .getSegments().stream()
                .filter(segment -> segment.getType().equals("class"))
                .map(UniqueId.Segment::getValue)
                .collect(Collectors.joining());
    }

    private TestIdentifier getRoot(TestPlan testPlan) {
        return testPlan.getRoots().stream().findFirst().get();
    }

    @Override
    public void afterEach(final ExtensionContext context) throws Exception {
        SHARED.put(context.getDisplayName(), SharedParameter.asMap(context));
    }
}
