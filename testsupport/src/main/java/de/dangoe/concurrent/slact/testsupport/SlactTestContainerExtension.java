package de.dangoe.concurrent.slact.testsupport;

import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class SlactTestContainerExtension implements AfterEachCallback, ParameterResolver {

  private SlactTestContainer testContainer;

  @Override
  public void afterEach(ExtensionContext extensionContext) throws Exception {

    if (testContainer != null) {
      testContainer.close();
      this.testContainer = null;
    }
  }

  @Override
  public boolean supportsParameter(final @NotNull ParameterContext parameterContext,
      final @NotNull ExtensionContext extensionContext) throws ParameterResolutionException {

    return parameterContext.getParameter().getType() == SlactTestContainer.class;
  }

  @Override
  public Object resolveParameter(final @NotNull ParameterContext parameterContext,
      final @NotNull ExtensionContext extensionContext) throws ParameterResolutionException {

    if (this.testContainer == null) {
      this.testContainer = new SlactTestContainer();
    }

    return this.testContainer;
  }
}
