package com.celements.common.test;

import static com.google.common.base.Preconditions.*;
import static java.util.stream.Collectors.*;
import static org.easymock.EasyMock.*;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.apache.velocity.VelocityContext;
import org.xwiki.component.manager.ComponentRepositoryException;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.MockConfigurationSource;

import com.google.common.base.Strings;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.ListClass;
import com.xpn.xwiki.objects.classes.PropertyClass;
import com.xpn.xwiki.store.XWikiStoreInterface;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.web.XWikiMessageTool;

public final class CelementsTestUtils {

  public static final String DEFAULT_DB = "xwikidb";
  public static final String DEFAULT_MAIN_WIKI = "xwikiWiki";
  public static final String DEFAULT_LANG = "de";

  private CelementsTestUtils() {}

  @SuppressWarnings("unchecked")
  private static <T> T getFromExecContext(String key, Supplier<T> defaultSupplier) {
    ExecutionContext ctx = Utils.getComponent(Execution.class).getContext();
    T value = (T) ctx.getProperty(key);
    if (value == null) {
      value = defaultSupplier.get();
      ctx.setProperty(key, value);
    }
    return value;
  }

  public static Collection<Object> getDefaultMocks() {
    return CelementsSpringTestUtil.streamDefaultMocks().collect(toList());
  }

  public static <T> T createMockAndAddToDefault(final Class<T> toMock) {
    return CelementsSpringTestUtil.createDefaultMock(toMock);
  }

  public static <T> T getMock(final Class<T> mockClass) {
    return CelementsSpringTestUtil.getMock(mockClass);
  }

  public static XWiki getWikiMock() {
    XWiki wikiMock = getContext().getWiki();
    if (wikiMock == null) {
      wikiMock = createMockAndAddToDefault(XWiki.class);
      getContext().setWiki(wikiMock);
    }
    return wikiMock;
  }

  public static XWikiContext getContext() {
    return getFromExecContext(XWikiContext.EXECUTIONCONTEXT_KEY, () -> {
      XWikiContext context = new XWikiContext();
      context.setDatabase(DEFAULT_DB);
      context.setMainXWiki(DEFAULT_MAIN_WIKI);
      context.setLanguage(DEFAULT_LANG);
      Locale locale = new Locale(context.getLanguage());
      ResourceBundle bundle = ResourceBundle.getBundle("ApplicationResources", locale);
      if (bundle == null) {
        bundle = ResourceBundle.getBundle("ApplicationResources");
      }
      XWikiMessageTool msg = new TestMessageTool(bundle, context);
      context.put("msg", msg);
      VelocityContext vcontext = ((VelocityContext) context.get("vcontext"));
      if (vcontext != null) {
        vcontext.put("msg", msg);
        vcontext.put("locale", locale);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> gcontext = (Map<String, Object>) context.get("gcontext");
      if (gcontext != null) {
        gcontext.put("msg", msg);
        gcontext.put("locale", locale);
      }
      return context;
    });
  }

  public static XWikiStoreInterface getStoreMock() throws ComponentRepositoryException {
    XWikiStoreInterface storeMock = getMock(XWikiStoreInterface.class);
    if (storeMock == null) {
      storeMock = registerComponentMock(XWikiStoreInterface.class);
      expect(getWikiMock().getStore()).andReturn(storeMock).anyTimes();
    }
    return storeMock;
  }

  public static TestMessageTool getMessageToolStub() {
    XWikiMessageTool msgTool = getContext().getMessageTool();
    if (msgTool instanceof TestMessageTool) {
      return (TestMessageTool) msgTool;
    }
    return null;
  }

  public static XWikiDocument createDocMock(DocumentReference docRef) {
    XWikiDocument docMock = createMockAndAddToDefault(XWikiDocument.class);
    expect(docMock.getDocumentReference()).andReturn(docRef).anyTimes();
    return docMock;
  }

  public static BaseClass expectNewBaseObject(final DocumentReference classRef)
      throws XWikiException {
    BaseClass bClass = createBaseClassMock(classRef);
    expect(bClass.newCustomClassInstance(same(getContext()))).andAnswer(() -> {
      BaseObject bObj = new BaseObject();
      bObj.setXClassReference(classRef);
      return bObj;
    }).anyTimes();
    return bClass;
  }

  public static BaseClass expectPropertyClass(DocumentReference classRef, String fieldName,
      PropertyClass propClass) throws XWikiException {
    BaseClass bClass = createBaseClassMock(classRef);
    expectPropertyClass(bClass, fieldName, propClass);
    return bClass;
  }

  public static BaseClass expectPropertyClass(BaseClass bClass, String fieldName,
      PropertyClass propClass) {
    expect(bClass.get(eq(fieldName))).andReturn(propClass).anyTimes();
    return bClass;
  }

  public static BaseClass expectPropertyClasses(DocumentReference classRef,
      Map<String, PropertyClass> fieldMap) throws XWikiException {
    BaseClass bClass = createBaseClassMock(classRef);
    expectPropertyClasses(bClass, fieldMap);
    return bClass;
  }

  public static BaseClass expectPropertyClasses(BaseClass bClass,
      Map<String, PropertyClass> fieldMap) {
    for (Entry<String, PropertyClass> fieldEntry : fieldMap.entrySet()) {
      expectPropertyClass(bClass, fieldEntry.getKey(), fieldEntry.getValue());
    }
    return bClass;
  }

  public static @NotNull BaseClass adjustDefaultListSeparator(@NotNull BaseClass bClass,
      @NotEmpty String fieldName) {
    return adjustListSeparator(bClass, fieldName, "|");
  }

  public static @NotNull BaseClass adjustListSeparator(@NotNull BaseClass bClass,
      @NotEmpty String fieldName, @NotEmpty String separator) {
    checkNotNull(bClass);
    checkArgument(!Strings.isNullOrEmpty(fieldName));
    checkArgument(!Strings.isNullOrEmpty(separator));
    ((ListClass) bClass.get(fieldName)).setSeparators(separator);
    return bClass;
  }

  public static BaseClass createBaseClassMock(DocumentReference classRef) throws XWikiException {
    BaseClass bClass = createMockAndAddToDefault(BaseClass.class);
    expect(getWikiMock().getXClass(eq(classRef), same(getContext()))).andReturn(bClass).anyTimes();
    expect(bClass.getXClassReference()).andReturn(classRef).anyTimes();
    return bClass;
  }

  public static void setConfigSrcProperty(String key, Object value) {
    ((MockConfigurationSource) Utils.getComponent(ConfigurationSource.class))
        .setProperty(key, value);
  }

  public static void registerComponentMocks(Class<?>... roles) throws ComponentRepositoryException {
    CelementsSpringTestUtil.registerComponentMocks(roles);
  }

  public static <T> T registerComponentMock(Class<T> role) throws ComponentRepositoryException {
    return CelementsSpringTestUtil.registerComponentMock(role);
  }

  public static <T> T registerComponentMock(Class<T> role, String hint)
      throws ComponentRepositoryException {
    return CelementsSpringTestUtil.registerComponentMock(role, hint);
  }

  public static <T> T registerComponentMock(Class<T> role, String hint, T componentMock)
      throws ComponentRepositoryException {
    return CelementsSpringTestUtil.registerComponent(role, hint, componentMock);
  }

  public static void replayDefault(Object... mocks) {
    CelementsSpringTestUtil.replayDefault(mocks);
  }

  public static void verifyDefault(Object... mocks) {
    CelementsSpringTestUtil.resetDefault(mocks);
  }

  public static void resetDefault(Object... mocks) {
    CelementsSpringTestUtil.resetDefault(mocks);
  }

}
