class VersionService 
implements remote.Version
{

    boolean transactional = false
    String stversion = "gridjobs 0.3.1"
    static expose = ['hessian']

    String version() {
       return stversion
    }

}
