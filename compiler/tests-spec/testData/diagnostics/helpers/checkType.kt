fun <T> checkSubtype(t: T) = t
class Inv<T>
fun <E> Inv<E>.check() {}

infix fun <T> T.checkType(f: Inv<T>.() -> Unit) {}
