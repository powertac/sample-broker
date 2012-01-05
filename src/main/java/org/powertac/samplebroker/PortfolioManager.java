package org.powertac.samplebroker;

public interface PortfolioManager
{

  // --------------- activation -----------------
  /**
   * Runs per-timeslot activities. If we have no tariffs, then we create and
   * issue some. If we have tariffs that are underperforming, then we think
   * about issuing some new ones.
   */
  public void activate ();
  
  /**
   * Returns total net expected usage across all subscriptions for the given
   * index (normally a timeslot serial number).
   */
  public double collectUsage (int index); 
}