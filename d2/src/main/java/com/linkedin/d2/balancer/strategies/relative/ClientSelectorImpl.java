package com.linkedin.d2.balancer.strategies.relative;

import com.linkedin.d2.balancer.KeyMapper;
import com.linkedin.d2.balancer.clients.TrackerClient;
import com.linkedin.d2.balancer.strategies.ClientSelector;
import com.linkedin.d2.balancer.strategies.LoadBalancerStrategy;
import com.linkedin.d2.balancer.util.hashing.HashFunction;
import com.linkedin.d2.balancer.util.hashing.Ring;
import com.linkedin.r2.message.Request;
import com.linkedin.r2.message.RequestContext;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 */
public class ClientSelectorImpl implements ClientSelector
{
  private static final Logger LOG = LoggerFactory.getLogger(ClientSelectorImpl.class.getName());

  private final HashFunction<Request> _requestHashFunction;

  public ClientSelectorImpl(HashFunction<Request> requestHashFunction)
  {
    _requestHashFunction = requestHashFunction;
  }

  /**
   * @return TrackerClient
   */
  @Nullable
  @Override
  public TrackerClient getTrackerClient(Request request,
                                        RequestContext requestContext,
                                        Ring<URI> ring,
                                        Map<URI, TrackerClient> trackerClients)
  {
    TrackerClient trackerClient;

    URI targetHostUri = KeyMapper.TargetHostHints.getRequestContextTargetHost(requestContext);

    if (targetHostUri != null)
    {
      trackerClient = trackerClients.get(targetHostUri);
    }
    else
    {
      trackerClient = getTrackerClientFromRing(request, requestContext, ring, trackerClients);
    }

    addToExcludedHosts(trackerClient, requestContext);

    return trackerClient;
  }

  private void addToExcludedHosts(TrackerClient trackerClient, RequestContext requestContext)
  {
    if (trackerClient != null)
    {
      LoadBalancerStrategy.ExcludedHostHints.addRequestContextExcludedHost(requestContext, trackerClient.getUri());
    }
  }

  private TrackerClient getTrackerClientFromRing(Request request,
                                                 RequestContext requestContext,
                                                 Ring<URI> ring,
                                                 Map<URI, TrackerClient> trackerClients)
  {
    if (ring == null) // TODO is this possible?
    {
      return null;
    }

    Set<URI> excludedUris = LoadBalancerStrategy.ExcludedHostHints.getRequestContextExcludedHosts(requestContext);
    int hashCode = _requestHashFunction.hash(request);
    URI uri = ring.get(hashCode);

    TrackerClient trackerClient = trackerClients.get(uri);

    if (trackerClient == null || excludedUris.contains(uri))
    {
      // Find next available URI.
      Iterator<URI> ringIterator = ring.getIterator(hashCode);

      while (ringIterator.hasNext())
      {
        uri = ringIterator.next();
        trackerClient = trackerClients.get(uri);

        if (trackerClient != null && !excludedUris.contains(uri))
        {
          break;
        }
      }
    }

    return trackerClient;
  }

  @Override
  public HashFunction<Request> getRequestHashFunction()
  {
    return _requestHashFunction;
  }
}
