package org.sonatype.nexus.repository.view;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.internal.describe.Description;
import org.sonatype.nexus.repository.internal.describe.DescriptionRenderer;
import org.sonatype.sisu.litmus.testsupport.TestSupport;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.stubbing.OngoingStubbing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class ConfigurableViewFacetDescribeTest
    extends TestSupport
{
  @Mock
  private Repository repository;

  @Mock
  private Request request;

  @Mock
  private Router router;

  @Mock
  private Response routerResponse;

  @Mock
  private Description description;

  @Mock
  private Response descriptionResponse;

  private ConfigurableViewFacet viewFacet = spy(new ConfigurableViewFacet(mock(DescriptionRenderer.class)));

  @Before
  public void initFacet() {
    viewFacet.configure(router);
    doReturn(repository).when(viewFacet).getRepository();
    doReturn(description).when(viewFacet).descriptionBuilder(any(Request.class));

    // Stub out the conversion to a response
    doReturn(descriptionResponse).when(viewFacet).toHtmlResponse(description);
  }

  @Test
  public void normalRequestReturnsRouterResponse() throws Exception {
    descriptionRequested(false);
    whenRequestDispatched().thenReturn(routerResponse);

    final Response facetResponse = viewFacet.dispatch(request);

    assertThat(facetResponse, is(equalTo(routerResponse)));
  }

  @Test
  public void describeRequestReturnsDescribeResponse() throws Exception {
    descriptionRequested(true);
    whenRequestDispatched().thenReturn(routerResponse);

    final Response facetResponse = viewFacet.dispatch(request);

    assertThat(facetResponse, is(equalTo(descriptionResponse)));
  }

  @Test
  public void routerExceptionsAreDescribed() throws Exception {
    descriptionRequested(true);
    whenRequestDispatched().thenThrow(new RuntimeException());

    final Response facetResponse = viewFacet.dispatch(request);

    assertThat(facetResponse, is(equalTo(descriptionResponse)));
  }

  private OngoingStubbing<Response> whenRequestDispatched() throws Exception
  {
    return when(router.dispatch(repository, request, description));
  }

  private void descriptionRequested(final boolean describe) {
    when(description.isDescribing()).thenReturn(describe);
  }

}