class CierreService 
implements remote.Cierre
{

    boolean transactional = true
    static expose = ['hessian']

    Object cierre(Closure c, List y) {
       return c(*y)
    }
}
