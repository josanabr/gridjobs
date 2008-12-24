/**
This class records some hardware characteristics of the clusters.
For instance, it saves the number of compute nodes(CN) in a cluster, how many processors every CN has,
the processor speed and the last date when the monitoring was carry out.

This class is accessed by the MonitorService and can also be accessed by the schedulers.
*/
class Resourcecharacteristics {
   static belongsTo = [Gridresource]

   Gridresource gridresource
   int numnodes
   int cpupernode
   int dead
   int inuse
   double cpuspeed
   Date lastmodified

   static constraints = {
      gridresource(nullable:false)
      numnodes(nullable:false)
      dead(nullable:false)
      cpupernode(nullable:false)
      cpuspeed(nullable:false)
      lastmodified(nullable:false)
      inuse(nullable:false)
   }
}
