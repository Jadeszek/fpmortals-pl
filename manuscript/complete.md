
# For Comprehensions

`for comprehension` to idealna abstrakcja do pisania funkcyjnych programów komunikujących się ze światem.
Ponieważ będziemy używać jej bardzo często poświęcimy chwilę na przypomnienie sobie jej zasad działania a
przy okazji zobaczymy jak Scalaz może pomóc nam pisać czystszy kod.

Ten rozdział nie skupia się na programowaniu czysto funkcyjnym a techniki w nim opisane znajdą zastosowanie
również w aplikacjach niefunkcyjnych.

## Syntax Sugar

Konstrukcja `for` w Scali jest prostą reguła przepisania (_rewrite rule_), zwaną również *syntax sugar*[^syntaxsugar], 
i nie wnosi żadnych dodatkowych informacji do naszego programu.

[^syntaxsugar]: Tłumaczenie _cukier składniowy_ jest tak niedorzeczne, że zdecydowaliśmy pozostać przy wersji angielskiej.

Aby zobaczyć co tak na prawdę robi `for` użyjemy funkcji `show` i `reify` dostępnych w REPLu. Dzięki nim możemy
wypisać kod w formie jaką przyjmuje po inferencji typów (_type inference_). 

{lang="text"}
~~~~~~~~
  scala> import scala.reflect.runtime.universe._
  scala> val a, b, c = Option(1)
  scala> show { reify {
           for { i <- a ; j <- b ; k <- c } yield (i + j + k)
         } }
  
  res:
  $read.a.flatMap(
    ((i) => $read.b.flatMap(
      ((j) => $read.c.map(
        ((k) => i.$plus(j).$plus(k)))))))
~~~~~~~~

Widzimy dużo szumu spowodowanego dodatkowymi wzbogaceniami (np. `+` jest przepisany jako `$plus`, itp.). 
Dla zwiększenia zwięzłości w dalszych przykładach pominiemy wywołania `show` oraz `reify` kiedy linia rozpoczyna się 
od `reify>`. Dodatkowo generowany kod poddamy ręcznemu oczyszczeniu aby nie rozpraszać czytelnika.

{lang="text"}
~~~~~~~~
  reify> for { i <- a ; j <- b ; k <- c } yield (i + j + k)
  
  a.flatMap {
    i => b.flatMap {
      j => c.map {
        k => i + j + k }}}
~~~~~~~~

Zasadą kciuka jest, że każdy `<-` (zwany *generatorem*) jest równoznaczny z zagnieżdżonym wywołaniem `flatMap`,
z wyjątkiem ostatniego, który jest wywołaniem funkcji `map` do której przekazane zostaje ciało bloku `yield`.

### Przypisania

Możemy bezpośrednio przypisywać wartości do zmiennych za pomocą wyrażeń typu `ij = i + j` (słowo kluczowe `val` nie jest wymagane)

{lang="text"}
~~~~~~~~
  reify> for {
           i <- a
           j <- b
           ij = i + j
           k <- c
         } yield (ij + k)
  
  a.flatMap {
    i => b.map { j => (j, i + j) }.flatMap {
      case (j, ij) => c.map {
        k => ij + k }}}
~~~~~~~~

Wywołanie `map` na `b` wprowadza zmienną `ij` która jest flat-mapowana razem z `j`, a na końcu
wołane jest ostateczne `map` wykorzystujące kod z bloku `yield`.

Niestety nie możemy deklarować przypisań przed użyciem generatora. Funkcjonalność ta
została zasugerowana ale nie została jeszcze zaimplementowana: 
<https://github.com/scala/bug/issues/907>

{lang="text"}
~~~~~~~~
  scala> for {
           initial = getDefault
           i <- a
         } yield initial + i
  <console>:1: error: '<-' expected but '=' found.
~~~~~~~~

Możemy obejść to ograniczenie poprzez zadeklarowanie zmiennej poza konstrukcją `for`

{lang="text"}
~~~~~~~~
  scala> val initial = getDefault
  scala> for { i <- a } yield initial + i
~~~~~~~~

lub poprzez stworzenie `Option` z pierwotnej wartości

{lang="text"}
~~~~~~~~
  scala> for {
           initial <- Option(getDefault)
           i <- a
         } yield initial + i
~~~~~~~~


A> `val` oprócz przypisywania pojedynczych wartości wspiera także dowolne wyrażenia poprawne w kontekście `case` znanym z pattern matchingu.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> val (first, second) = ("hello", "world")
A>   first: String = hello
A>   second: String = world
A>   
A>   scala> val list: List[Int] = ...
A>   scala> val head :: tail = list
A>   head: Int = 1
A>   tail: List[Int] = List(2, 3)
A> ~~~~~~~~
A> 
A> Tak samo działa przypisywanie wewnątrz `for`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> val maybe = Option(("hello", "world"))
A>   scala> for {
A>            entry <- maybe
A>            (first, _) = entry
A>          } yield first
A>   res: Some(hello)
A> ~~~~~~~~
A> 
A> Trzeba jednak uważać aby nie pominąć żadnego z wariantów gdyż skutkować to będzie wyjątkiem wyrzuconym w trakcie 
A> działania programu (zaburzenie *totalności*).
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> val a :: tail = list
A>   caught scala.MatchError: List()
A> ~~~~~~~~


### Filter

Możemy umieścić wyrażenie `if` za generatorem aby ograniczyć wartości za pomocą predykatu

{lang="text"}
~~~~~~~~
  reify> for {
           i  <- a
           j  <- b
           if i > j
           k  <- c
         } yield (i + j + k)
  
  a.flatMap {
    i => b.withFilter {
      j => i > j }.flatMap {
        j => c.map {
          k => i + j + k }}}
~~~~~~~~

Starsze wersje Scali używały metody `filter`, ale ponieważ `Traversable.filter` tworzy nową kolekcje dla każdego predykatu,
wprowadzono metodę `withFilter` jako bardziej wydajną alternatywę. Możemy przypadkowo wywołać `withFilter` podając informację 
co do oczekiwanego typu, którą kompilator interpretuje jako pattern matching.

{lang="text"}
~~~~~~~~
  reify> for { i: Int <- a } yield i
  
  a.withFilter {
    case i: Int => true
    case _      => false
  }.map { case i: Int => i }
~~~~~~~~

Podobnie do przypisywania zmiennych, generatory mogą używać pattern matchingu po swojej lewej stronie. W przeciwieństwie 
do przypisań (które rzucają `MatchError` w przypadku niepowodzenia), generatory są *filtrowane* i nie rzucają wyjątków 
w czasie wykonania. Niestety dzieje się to kosztem podwójnego zaaplikowania wzoru (_pattern_).

A> Plugin kompilatora [`better-monadic-for`](https://github.com/oleg-py/better-monadic-for) produkuje alternatywną, **lepszą**
A> wersję kodu niż oryginalny kompilator Scali. Ten przykład jest interpretowany jako:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   reify> for { i: Int <- a } yield i
A>   
A>   a.map { (i: Int) => i}
A> ~~~~~~~~
A> 
A> zamiast nieefektywnego podwójnego dopasowywania (_matching_) (w najlepszym przypadku) i potajemnego filtrowania
A> wartości w czasie wykonania (w przypadku najgorszym). Używanie wysoce zalecane.

### For Each

W końcu, jeśli nie użyjemy słowa `yield`, kompilator użyje metody `foreach` zamiast `flatMap`, która użyteczna
jest jedynie w przypadku użycia efektów ubocznych.

{lang="text"}
~~~~~~~~
  reify> for { i <- a ; j <- b } println(s"$i $j")
  
  a.foreach { i => b.foreach { j => println(s"$i $j") } }
~~~~~~~~


### Podsumowanie

Pełen zbiór metod używanych przez konstrukcję `for` nie ma jednego wspólnego interfejsu; każde użycie jest 
niezależnie kompilowane. Gdyby taki interfejs istniał wyglądałby mniej więcej tak:

{lang="text"}
~~~~~~~~
  trait ForComprehensible[C[_]] {
    def map[A, B](f: A => B): C[B]
    def flatMap[A, B](f: A => C[B]): C[B]
    def withFilter[A](p: A => Boolean): C[A]
    def foreach[A](f: A => Unit): Unit
  }
~~~~~~~~

Jeśli kontekst (`C[_]`) konstrukcji `for` nie dostarcza swoich własnych metod `map` i `flatMap` 
to nie wszystko jeszcze stracone. Jeśli dostępna jest niejawna (_implicit_)  instancja typu `scalaz.Bind[T]` dla `T`
to dostarczy ona potrzebne metody `map` oraz `flatMap`.

A> Developerzy często zaskakiwani są faktem, że operacje oparte o typ `Future` i zdefiniowane wewnątrz konstrukcji `for`
A> nie są wykonywane równolegle:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   import scala.concurrent._
A>   import ExecutionContext.Implicits.global
A>   
A>   for {
A>     i <- Future { expensiveCalc() }
A>     j <- Future { anotherExpensiveCalc() }
A>   } yield (i + j)
A> ~~~~~~~~
A> 
A> Dzieję się tak ponieważ funkcja przekazana do metody `flatMap`, która wywołuje `anotherExpensiveCalc`, wykonuje się wyłącznie
A> **po** zakończeniu `expensiveCalc`. Aby wymusić równoległe wykonanie tych dwóch operacji musimy utworzyć je poza
A> konstrukcją `for`.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   val a = Future { expensiveCalc() }
A>   val b = Future { anotherExpensiveCalc() }
A>   for { i <- a ; j <- b } yield (i + j)
A> ~~~~~~~~
A> 
A> Konstrukcja `for` zaprojektowana jest wyłącznie do definiowania programów sekwencyjnych. W jednym z następnych
A> rozdziałów pokażemy o wiele lepszą metodę definiowania obliczeń równoległych. Spoiler: nie używaj typu `Future`.


## Nieszczęśliwa ścieżka[^unhappypath]

[^unhappypath]: Jest to kuriozalne tłumaczenie wyrażenia _unhappy path_. Ale wydało nam się całkiem zabawne.

Jak dotąd patrzyliśmy jedynie na reguły przepisywania, nie natomiast no to co dzieje się wewnątrz metod `map` i `flatMap`.
Zastanówmy się co dzieje się kiedy kontekst `for` zadecyduje że nie może kontynuować działania.

W przykładzie bazującym na typie `Option`, blok `yield` jest wywoływany jest jedynie kiedy wartości wszystkich zmiennych 
`i,j,k` są zdefiniowane.

{lang="text"}
~~~~~~~~
  for {
    i <- a
    j <- b
    k <- c
  } yield (i + j + k)
~~~~~~~~

Jeśli którakolwiek ze zmiennych `a,b,c` przyjmie wartość `None`, konstrukcja `for` zrobi zwarcie[^zwarcie] i zwróci `None` nie mówiąc
nam co poszło nie tak.

[^zwarcie] Z angielskiego _short-circuits_. Chodzi tutaj o zakończenie przetwarzania bez wykonywania pozostałych instrukcji.

A> W praktyce możemy zobaczyć wiele funkcji z parametrami typu `Option`, które w rzeczywistości muszą być zdefiniowane
A> aby uzyskać sensowny rezultat. Alternatywą do rzucania wyjątku jest użycie konstrukcji `for`, która zapewni nam totalność
A> naszej funkcji (zwrócenie wartości dla każdego możliwego argumentu):
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def namedThings(
A>     someName  : Option[String],
A>     someNumber: Option[Int]
A>   ): Option[String] = for {
A>     name   <- someName
A>     number <- someNumber
A>   } yield s"$number ${name}s"
A> ~~~~~~~~
A> 
A> jest to jednak rozwiązanie rozwlekłe, niezdarne i w złym stylu. Jeśli funkcja wymaga wszystkich swoich argumentów
A> to powinna zadeklarować takie wymaganie jawnie, spychając tym samym obowiązek obsłużenia brakujących wartości na 
A> wywołującego tęże funkcję.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def namedThings(name: String, num: Int) = s"$num ${name}s"
A> ~~~~~~~~

Jeśli użyjemy typu `Either`, wtedy to `Left` powodować będzie zwarcie konstrukcji `for` z dodatkową informacją którą niesie w sobie.
Rozwiązanie to jest zdecydowanie lepsze w przypadku raportowania błędów niż użycie typu `Option`:

{lang="text"}
~~~~~~~~
  scala> val a = Right(1)
  scala> val b = Right(2)
  scala> val c: Either[String, Int] = Left("sorry, no c")
  scala> for { i <- a ; j <- b ; k <- c } yield (i + j + k)
  
  Left(sorry, no c)
~~~~~~~~

Na koniec spójrzmy co stanie się z typem `Future`, który zawiedzie

{lang="text"}
~~~~~~~~
  scala> import scala.concurrent._
  scala> import ExecutionContext.Implicits.global
  scala> for {
           i <- Future.failed[Int](new Throwable)
           j <- Future { println("hello") ; 1 }
         } yield (i + j)
  scala> Await.result(f, duration.Duration.Inf)
  caught java.lang.Throwable
~~~~~~~~

Wartość `Future`, która wypisuje wiadomość do terminala nie jest nigdy tworzona, ponieważ, 
tak jak w przypadku `Option` i `Either`, konstrukcja `for` zwiera obwód i zakańcza przetwarzanie.

Zwieranie obwodu w przypadku odejścia od oczekiwanej ścieżki przetwarzania jest ważnym i często spotykanym rozwiązaniem.
Konstrukcja `for` nie jest w stanie obsłużyć uprzątnięcia zasobów (_resource cleanup_): nie ma możliwości wyrażenia zachowania 
podobnego do `try`/`finally`. Rozwiązanie to jest dobre, gdyż w programowaniu funkcyjnym jasno deklaruje że to kontekst 
(który zazwyczaj jest `Monad`ą, jak zobaczymy później), a nie logika biznesowa, jest odpowiedzialny za obsługę błędów 
i uprzątnięcie zasobów.


## Gimnastyka

Chociaż łatwo jest przepisać prosty kod sekwencyjny przy pomocy konstrukcji `for`,
czasami chcielibyśmy zrobić coś co wymaga mentalnych fikołków. Ten rozdział zbiera
niektóre praktyczne przykłady i pokazuje jak sobie z nimi radzić.


### Wyjście awaryjne

Powiedzmy że wywołujemy metodę, która zwraca typ `Option`. Jeśli wywołanie to się nie powiedzie,
chcielibyśmy użyć innej metody (i tak dalej i tak dalej), np. gdy używamy cache'a.

{lang="text"}
~~~~~~~~
  def getFromRedis(s: String): Option[String]
  def getFromSql(s: String): Option[String]
  
  getFromRedis(key) orElse getFromSql(key)
~~~~~~~~

Jeśli chcemy zrobić to samo poprzez asynchroniczną wersję tego samego API

{lang="text"}
~~~~~~~~
  def getFromRedis(s: String): Future[Option[String]]
  def getFromSql(s: String): Future[Option[String]]
~~~~~~~~

musimy uważać aby nie spowodować zbędnych obliczeń, ponieważ

{lang="text"}
~~~~~~~~
  for {
    cache <- getFromRedis(key)
    sql   <- getFromSql(key)
  } yield cache orElse sql
~~~~~~~~

uruchomi oba zapytania. Możemy użyć pattern matchingu na pierwszym rezultacie, ale typ się nie zgadza

{lang="text"}
~~~~~~~~
  for {
    cache <- getFromRedis(key)
    res   <- cache match {
               case Some(_) => cache !!! wrong type !!!
               case None    => getFromSql(key)
             }
  } yield res
~~~~~~~~

Musimy stworzyć `Future` ze zmiennej `cache`

{lang="text"}
~~~~~~~~
  for {
    cache <- getFromRedis(key)
    res   <- cache match {
               case Some(_) => Future.successful(cache)
               case None    => getFromSql(key)
             }
  } yield res
~~~~~~~~

`Future.successful` tworzy nową wartość typu `Future`, podobnie jak konstruktor typu `Option` lub
`List`.


### Wczesne wyjście

Powiedzmy, że znamy warunek, który pozwala nam szybciej zakończyć obliczenia z poprawną wartością.

Jeśli chcemy zakończyć je szybciej z błędem, standardowym sposobem na zrobienie tego w OOP[^oop] jest rzucenie wyjątku

[^oop]: _Object Oriented Programming_

{lang="text"}
~~~~~~~~
  def getA: Int = ...
  
  val a = getA
  require(a > 0, s"$a must be positive")
  a * 10
~~~~~~~~

co można zapisać asynchronicznie jako

{lang="text"}
~~~~~~~~
  def getA: Future[Int] = ...
  def error(msg: String): Future[Nothing] =
    Future.failed(new RuntimeException(msg))
  
  for {
    a <- getA
    b <- if (a <= 0) error(s"$a must be positive")
         else Future.successful(a)
  } yield b * 10
~~~~~~~~

Lecz jeśli chcemy zakończyć obliczenia z poprawna wartością, prosty kod synchroniczny:

{lang="text"}
~~~~~~~~
  def getB: Int = ...
  
  val a = getA
  if (a <= 0) 0
  else a * getB
~~~~~~~~

przekłada się na zagnieżdżone konstrukcje `for` gdy tylko nasze zależności stają się asynchroniczne:

{lang="text"}
~~~~~~~~
  def getB: Future[Int] = ...
  
  for {
    a <- getA
    c <- if (a <= 0) Future.successful(0)
         else for { b <- getB } yield a * b
  } yield c
~~~~~~~~

A> Jeśli dostępna jest domyślna instancja `Monad[T]` dla `T[_]` (co oznacza, że `T` jest monadyczne), wówczas
A> Scalaz pozwala nam tworzyć instancje `T[A]` używając wartości `a: A` poprzez wywołanie `a.pure[T]`.
A> 
A> Scalaz dostarcza instancję `Monad[Future]` a `.pure[Future]` wywołuje `Future.successful`.
A> Poza tym że `pure` jest nieco bardziej zwięzłe, jest to koncept ogólny, który aplikuje się do typów innych niż `Future`
A> a przez to jest to rozwiązanie rekomendowane.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   for {
A>     a <- getA
A>     c <- if (a <= 0) 0.pure[Future]
A>          else for { b <- getB } yield a * b
A>   } yield c
A> ~~~~~~~~


## Niepojmowalny

Kontekst którego używamy wewnątrz konstrukcji `for` musi być niezmienny: nie możemy mieszać wielu równych typów jak 
na przykład `Future` i `Option`.

{lang="text"}
~~~~~~~~
  scala> def option: Option[Int] = ...
  scala> def future: Future[Int] = ...
  scala> for {
           a <- option
           b <- future
         } yield a * b
  <console>:23: error: type mismatch;
   found   : Future[Int]
   required: Option[?]
           b <- future
                ^
~~~~~~~~

Nie ma nic, co pozwoliło by nam mieszać dowolne dwa konteksty wewnątrz konstrukcji `for`. Wynika 
to za faktu, że nie da się zdefiniować znaczenia takiej operacji.

Jednak gdy mamy do czynienia z zagnieżdżonymi kontekstami intencja jest zazwyczaj oczywista, tymczasem 
kompilator nadal nie przyjmuje naszego kodu.

{lang="text"}
~~~~~~~~
  scala> def getA: Future[Option[Int]] = ...
  scala> def getB: Future[Option[Int]] = ...
  scala> for {
           a <- getA
           b <- getB
         } yield a * b
                   ^
  <console>:30: error: value * is not a member of Option[Int]
~~~~~~~~

Chcielibyśmy aby konstrukcja `for` zajęła się zewnętrznym kontekstem i pozwoliła nam
skupić się modyfikacji zagnieżdżonej wartości typu `Option`. 
Ukrywaniem zewnętrznego kontekstu zajmują się tzw. transformatory monad (_monad transformers_), 
a Scalaz dostarcza nam implementacje tychże dla typów `Option` i `Either` nazywające się 
odpowiednio `OptionT` oraz `EitherT`.

Kontekst zewnętrzny może być dowolnym kontekstem który sam w sobie kompatybilny jest z konstrukcją `for`,
musi jedynie pozostać niezmienny.

Tworzymy instancję `OptionT` z każdego wywołania metody, zmieniając tym samym kontekst z `Future[Option[_]]` 
na `OptionT[Future, _]`.

{lang="text"}
~~~~~~~~
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
         } yield a * b
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

`.run` pozwala nam wrócić do oryginalnego kontekstu

{lang="text"}
~~~~~~~~
  scala> result.run
  res: Future[Option[Int]] = Future(<not completed>)
~~~~~~~~

Transformatory monad pozwalają nam mieszać wywołania funkcji zwracających `Future[Option[_]]` z
funkcjami zwracającymi po prostu `Future` poprzez `.liftM[OptionT]` (pochodzące ze scalaz):

{lang="text"}
~~~~~~~~
  scala> def getC: Future[Int] = ...
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
           c <- getC.liftM[OptionT]
         } yield a * b / c
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

Dodatkowo możemy mieszać wartości typu `Option` poprzez wywołanie
`Future.successful` (`.pure[Future]`) a następnie `OptionT`.

{lang="text"}
~~~~~~~~
  scala> def getD: Option[Int] = ...
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
           c <- getC.liftM[OptionT]
           d <- OptionT(getD.pure[Future])
         } yield (a * b) / (c * d)
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

Znów zrobił się bałagan, ale i tak jest lepiej niż gdybyśmy musieli ręcznie pisać zagnieżdżone 
wywołania metod `flatMap` oraz `map`. Możemy nieco uprzątnąć za pomocą  DSLa który obsłuży
wszystkie wymagane konwersje

{lang="text"}
~~~~~~~~
  def liftFutureOption[A](f: Future[Option[A]]) = OptionT(f)
  def liftFuture[A](f: Future[A]) = f.liftM[OptionT]
  def liftOption[A](o: Option[A]) = OptionT(o.pure[Future])
  def lift[A](a: A)               = liftOption(Option(a))
~~~~~~~~

w połączeniu z operatorem `|>`, który aplikuje funkcje podaną po prawej stronie na argumencie 
podanym z lewej strony, możemy wizualnie oddzielić logikę od transformacji.

{lang="text"}
~~~~~~~~
  scala> val result = for {
           a <- getA       |> liftFutureOption
           b <- getB       |> liftFutureOption
           c <- getC       |> liftFuture
           d <- getD       |> liftOption
           e <- 10         |> lift
         } yield e * (a * b) / (c * d)
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

A> `|>` jest często nazywany *operatorem drozda* z powodu jego podobieństwa do tego słodkiego ptaka.
A> Ci, który nie lubią operatorów symbolicznych mogą użyć aliasu `.into`. 

To podejście działa również dla `Either` (i innych) ale w ich przypadku metody pomocnicze są bardziej skomplikowane
i wymagają dodatkowy parametrów. Scalaz dostarcza wiele transformatorów
monad dla typów które definiuje, więc zawsze 
warto sprawdzić czy ten którego potrzebujemy jest dostępny.


# Projektowanie Aplikacji

W tym rozdziale napiszemy logikę biznesową oraz testy dla czysto funkcyjnej aplikacji serwerowej.
Kod źródłowy tej aplikacji dostępny jest wraz ze źródłami tej książki w katalogu `example`.
Nie mniej lepiej nie zagłębiać się w niego zanim nie dotrzemy do ostatniego rozdziału, gdyż
wraz z poznawaniem technik FP będziemy go istotnie zmieniać.

## Specyfikacja

Nasza aplikacja będzie zarządzać farmą serwerów, tworzoną na bazie zapotrzebowania i operującą z możliwie niskim
budżetem. Będzie ona nasłuchiwać wiadomości od serwera CI [Drone](https://github.com/drone/drone) i uruchamiać
agenty (maszyny robocze) używając [Google Container Engine](https://cloud.google.com/container-engine/) (GKE), tak aby 
zaspokoić potrzeby kolejki zadań.

{width=60%}
![](images/architecture.png)

Drone otrzymuje pracę do wykonania kiedy kontrybutor zgłasza pull request w obsługiwanym projekcie na githubie.
Drone przydziela pracę swoim agentom, gdzie każdy z nich przetwarza jedno zadanie w danym momencie.

Zadaniem naszej aplikacji jest zagwarantować że zawsze jest dość agentów aby wykonać potrzebną pracę, 
jednocześnie dbając aby ich liczba nie przekroczyła określonej granicy i minimalizując całkowite koszta.
Nasza aplikacja musi znać liczbę elementów w *kolejce* i liczbę dostępnych *agentów*.

Google potrafi tworzyć węzły (_nodes_), każdy z nich może być gospodarzem dla wielu agentów równocześnie.
Agent podczas startu rejestruje się w serwerze, który od tej pory kontroluje jego cykl życia (wliczając 
cykliczne weryfikowanie czy agent jest nadal aktywny).

GKE pobiera opłatę za każdą minutę działania węzła, zaokrąglając czas do najbliższej godziny. Aby osiągnąć maksymalną 
efektywność nie możemy po prostu tworzyć nowych węzłów dla każdego zadania. Zamiast tego powinniśmy reużywać
wcześniej stworzone węzły i utrzymywać je do 58 minuty ich działania. 

Nasza aplikacja musi być w stanie uruchamiać i zatrzymywać węzły, sprawdzać ich status (np. czas działania, aktywność)
oraz wiedzieć jaki jest aktualny czas wg. GKE.

Dodatkowo, nie jest dostępne żadne API, które pozwoliłoby rozmawiać bezpośrednio z danym *agentem*, tak więc nie wiemy
czy aktualnie wykonuje on jakąś pracę dla serwera. Jeśli przypadkowo zatrzymamy agenta w czasie wykonywania pracy
jest to niewygodne, gdyż wymaga ludzkiej interakcji i ponownego startu zadania.

Kontrybutorzy mogą ręcznie dodawać agentów do farmy, tak więc liczba agentów i węzłów może być różna. Nie musimy
dostarczać węzłów jeśli dostępni są wolni agenci.

W przypadku awarii powinniśmy zawsze wybierać najtańszą opcję.

Zarówno Drone jak i GKE udostępniają JSONowe REST API zabezpieczone OAuth 2.0.


## Interfejsy / Algebry

Spróbujmy teraz skodyfikować diagram architektury z poprzedniego rozdziału. Po pierwsze powinniśmy zdefiniować
prosty typ danych do przechowywania znacznika czasu z dokładnością do milisekund. Niestety typ taki nie
jest dostępny w bibliotece standardowej Javy ani Scali.

{lang="text"}
~~~~~~~~
  import scala.concurrent.duration._
  
  final case class Epoch(millis: Long) extends AnyVal {
    def +(d: FiniteDuration): Epoch = Epoch(millis + d.toMillis)
    def -(e: Epoch): FiniteDuration = (millis - e.millis).millis
  }
~~~~~~~~

W FP *algebra* zajmuje miejsce *interfejsu* z Javy lub zbioru poprawnych wiadomości obsługiwanych
przez aktora z Akki. W tej właśnie warstwie definiujemy wszystkie operacje naszego systemu które 
prowadzą do komunikacji ze światem zewnętrznym a tym samym do efektów ubocznych.

Istnieje ścisła więź między algebrami a logiką biznesową. Często przechodzić będziemy przez kolejne iteracje,
w których próbujemy zamodelować nasz problem, następnie implementujemy rozwiązanie, tylko po to aby przekonać się
że nasz model i zrozumienie problemu wcale nie było tak kompletne jak nam się wydawało.

{lang="text"}
~~~~~~~~
  trait Drone[F[_]] {
    def getBacklog: F[Int]
    def getAgents: F[Int]
  }
  
  final case class MachineNode(id: String)
  trait Machines[F[_]] {
    def getTime: F[Epoch]
    def getManaged: F[NonEmptyList[MachineNode]]
    def getAlive: F[Map[MachineNode, Epoch]]
    def start(node: MachineNode): F[MachineNode]
    def stop(node: MachineNode): F[MachineNode]
  }
~~~~~~~~

Użyliśmy typu `NonEmptyList`, który można łatwo utworzyć wywołując metodę `.toNel` na standardowej liście, która zwraca
`Option[NonEmptyList]`. Poza tym wszystko powinno być jasne.


A> Dobrą praktyką w FP jest zakodowanie ograniczeń (_constraints_) zarówno w typach przyjmowanych **jak i** zwracanych z 
A> funkcji --- oznacza to że nigdy nie musimy obsługiwać sytuacji, które nie mają prawa się zdążyć. Jednocześnie
A> podejście to kłóci się z *prawem Postela* (_Postel's law_) "bądź liberalny względem tego co przyjmujesz od innych"[^postel].
A>
A> I chociaż zgadzamy się że typy parametrów powinny być tak ogólne jak to tylko możliwe, to nie zgadzamy się
A> że funkcja powinna przyjmować typ `Seq` jeśli nie potrafi obsłużyć pustej kolekcji tego typu. Inaczej zmuszeni jesteśmy
A> wyrzucić wyjątek tym samym tracąc totalność funkcji i powodując efekt uboczny. 
A>
A> Dlatego też wybieramy `NonEmptyList` nie dlatego że jest to lista, ale dlatego że gwarantuje ona nam obecność 
A> przynajmniej jednego elementu. Kiedy lepiej poznamy hierarchie typeclass ze Scalaz poznamy również lepszy sposób na
A> wyrażenie tej gwarancji.

[^postel]: _Be conservative in what you do, be liberal in what you accept from others_

## Logika Biznesowa

Teraz przyszedł czas na napisanie logiki biznesowej, która definiuje zachowanie naszej aplikacji.
Na razie rozpatrywać będziemy tylko szczęśliwy scenariusz (_happy path_).

Potrzebujemy klasy `WorldView` która przechowywać będzie zrzut naszej wiedzy o świecie. 
Gdybyśmy projektowali naszą aplikację przy użyciu Akki, `WorldView` najprawdopodobniej
zostałby zaimplementowany jako `var` wewnątrz stanowego aktora.

`WorldView` agreguje wartości zwracane przez wszystkie metody ze wcześniej zdefiniowanych algebr
oraz dodaje pole `pending` aby śledzić nieobsłużone jeszcze żądania.

{lang="text"}
~~~~~~~~
  final case class WorldView(
    backlog: Int,
    agents: Int,
    managed: NonEmptyList[MachineNode],
    alive: Map[MachineNode, Epoch],
    pending: Map[MachineNode, Epoch],
    time: Epoch
  )
~~~~~~~~

Teraz prawie gotowi jesteśmy aby zacząć pisać naszą logikę biznesową, ale musimy zadeklarować że zależy ona
od algebr `Drone` in `Machines`.

Możemy zacząć od interfejsu dla naszej logiki

{lang="text"}
~~~~~~~~
  trait DynAgents[F[_]] {
    def initial: F[WorldView]
    def update(old: WorldView): F[WorldView]
    def act(world: WorldView): F[WorldView]
  }
~~~~~~~~

i zaimplementować go za pomocą *modułu*. Moduł zależy wyłącznie od innych modułów, algebr i czystych funkcji oraz 
potrafi abstrahować nad `F`. Jeśli implementacja algebraicznego interfejsu zależy od konkretnego typu, np. `IO`,
nazywamy ją *interpreterem*.

{lang="text"}
~~~~~~~~
  final class DynAgentsModule[F[_]: Monad](D: Drone[F], M: Machines[F])
    extends DynAgents[F] {
~~~~~~~~

Ograniczenie kontekstu (_context bound_) poprzez typ `Monad` oznacza że `F` jest *monadyczne*, pozwalając nam tym samym na używanie
metod `map`, `pure`, i oczywiście, `flatmap` wewnątrz konstrukcji `for`.

Mamy dostęp do algebr `Drone` i `Machines` poprzez `D` i `M`. Używanie pojedynczych wielkich liter jest popularną konwencją
dla implementacji algebr i monad.

Nasza logika biznesowa działać będzie wewnątrz nieskończonej pętli (pseudokod)

{lang="text"}
~~~~~~~~
  state = initial()
  while True:
    state = update(state)
    state = act(state)
~~~~~~~~


### initial

Wewnątrz metody `initial` wywołujemy wszystkie zewnętrzne serwisy i zbieramy wyniki tych wywołań wewnątrz
instancji `WorldView`.  Pole `pending` domyślnie jest puste.

{lang="text"}
~~~~~~~~
  def initial: F[WorldView] = for {
    db <- D.getBacklog
    da <- D.getAgents
    mm <- M.getManaged
    ma <- M.getAlive
    mt <- M.getTime
  } yield WorldView(db, da, mm, ma, Map.empty, mt)
~~~~~~~~

Przypomnij sobie, jak w Rozdziale 1 mówiliśmy, że `flatMap` (używany wraz z generatorem `<-`)
pozwala nam operować na wartościach dostępnych w czasie wykonania. Kiedy zwracamy `F[_]` to tak na prawdę
zwracamy kolejny program który zostanie zinterpretowany w czasie wykonania. Na takim programie wywołujemy `flatMap`.
Tak właśnie możemy sekwencyjnie łączyć kod, który powoduje efekty uboczne, jednocześnie mogąc używać zupełnie czystej
(pozbawionej tychże efektów) implementacji w czasie testowania. FP może być przez to widziane jako Ekstremalne Mockowanie.


### update

Metoda `update` powinna wywołać `initial` aby odświeżyć nasz obraz świata, zachowując znane akcje oczekujące (pole `pending`).

Jeśli węzeł zmienił swój stan, usuwamy go z listy oczekujących, a jeśli akcja trwa dłużej niż 10 minut to zakładamy
że zakończyła się porażką i zapominamy że ją zainicjowaliśmy.

{lang="text"}
~~~~~~~~
  def update(old: WorldView): F[WorldView] = for {
    snap <- initial
    changed = symdiff(old.alive.keySet, snap.alive.keySet)
    pending = (old.pending -- changed).filterNot {
      case (_, started) => (snap.time - started) >= 10.minutes
    }
    update = snap.copy(pending = pending)
  } yield update
  
  private def symdiff[T](a: Set[T], b: Set[T]): Set[T] =
    (a union b) -- (a intersect b)
~~~~~~~~

Konkretne funkcje takie jak `.symdiff` nie wymagają testowych interpreterów, ponieważ mają wyrażone wprost zarówno
wejście jak i wyjście. Dlatego też moglibyśmy przenieść je do samodzielnego, bezstanowego obiektu, który można
testować w izolacji. Z radością testować będziemy tylko metody publiczne, ceniąc sobie czytelność logiki biznesowej.


### act

Metoda `act` jest nieco bardziej skomplikowana, więc dla zwiększenia czytelności podzielimy ją na dwie części:
wykrywanie akcji które należy wykonać oraz wykonywanie tychże akcji. To uproszczenie sprawia że możemy wykonać tylko 
jedną akcje per wywołanie, ale jest to całkiem rozsądne biorąc pod uwagę że możemy lepiej kontrolować wykonywane akcje
oraz wywoływać `act` tak długo aż nie pozostanie żadna akcja do wykonania.


Wykrywanie konkretnych scenariuszy piszemy jako ekstraktory bazujące na `WorldView`, co w praktyce jest
po prostu bardziej ekspresywną formą implementacji warunków `if` / `else`.

Musimy dodać agentów do farmy jeśli praca gromadzi się w kolejce, nie ma żadnych agentów,
nie ma aktywnych węzłów i nie ma żadnych akcji oczekujących an wykonanie. Zwracamy węzeł który chcielibyśmy 
uruchomić.

{lang="text"}
~~~~~~~~
  private object NeedsAgent {
    def unapply(world: WorldView): Option[MachineNode] = world match {
      case WorldView(backlog, 0, managed, alive, pending, _)
           if backlog > 0 && alive.isEmpty && pending.isEmpty
             => Option(managed.head)
      case _ => None
    }
  }
~~~~~~~~

Jeśli kolejka jest pusta, powinniśmy zatrzymać wszystkie nieaktywne (nie wykonujące żadnych zadań) węzły. 
Pamiętając, że Google zawsze pobiera opłatę za pełne godziny, wyłączamy węzły jedynie w 58 minucie ich działania.
Zwracamy listę węzłów do zatrzymania,

Jako zabezpieczenie finansowe zakładamy że żaden węzeł nie może żyć dłużej niż 5 godzin.

{lang="text"}
~~~~~~~~
  private object Stale {
    def unapply(world: WorldView): Option[NonEmptyList[MachineNode]] = world match {
      case WorldView(backlog, _, _, alive, pending, time) if alive.nonEmpty =>
        (alive -- pending.keys).collect {
          case (n, started) if backlog == 0 && (time - started).toMinutes % 60 >= 58 => n
          case (n, started) if (time - started) >= 5.hours => n
        }.toList.toNel
  
      case _ => None
    }
  }
~~~~~~~~

Gdy już zdefiniowaliśmy scenariusze, które nas interesują możemy przejść do implementacji metody `act`. 
Gdy chcemy aby węzeł został uruchomiony lub zatrzymany, dodajemy go do listy `pending` wraz z zapisem
czasu w którym tę akcję zaplanowaliśmy.

{lang="text"}
~~~~~~~~
  def act(world: WorldView): F[WorldView] = world match {
    case NeedsAgent(node) =>
      for {
        _ <- M.start(node)
        update = world.copy(pending = Map(node -> world.time))
      } yield update
  
    case Stale(nodes) =>
      nodes.foldLeftM(world) { (world, n) =>
        for {
          _ <- M.stop(n)
          update = world.copy(pending = world.pending + (n -> world.time))
        } yield update
      }
  
    case _ => world.pure[F]
  }
~~~~~~~~

Ponieważ `NeedsAgent` i `Stale` nie pokrywają wszystkich możliwych sytuacji musimy również zdefiniować
zachowanie domyślne, które nie robi nic. Przypomnienie z Rozdziału 2: `.pure` tworzy (monadyczny) kontekst używany 
wewnątrz `for` z prostej wartości.

`foldLeftM` działa podobnie do `foldLeft`, z tą różnicą że przyjmowana funkcja może zwracać wartość opakowaną w kontekst.
W naszym przypadku, każda iteracja zwraca `F[WorldView]`. `M` w nazwie jest skrótem od _Monadic_. Niedługo dowiemy się
więcej o tego typu *wyniesionych* (_lifted_) funkcjach, która zachowują się tak jak byśmy oczekiwali ale przyjmują
funkcje zwracające wartości monadyczne zamiast zwykłych wartości.


## Testy Jednostkowe

Podejście funkcyjne do pisania aplikacji jest marzeniem projektanta: można skupić się na logice biznesowej pozostawiając
implementacji algebr pozostałym członkom zespołu.

Nasza aplikacja bardzo silnie zależy od upływu czasu oraz zewnętrznych webserwisów. Gdyby była to tradycyjna aplikacja
napisania w duchu OOP, stworzylibyśmy mocki dla wszystkich wywołań lub testowych aktorów dla wysyłanych wiadomości.
Mockowanie w FP jest równoznaczne z dostarczeniem alternatywnej implementacji algebr od których zależymy. Algebry 
izolują części systemu, które muszą zostać *zamockowane*, czyli po prostu inaczej interpretowane w kontekście testów 
jednostkowych.

Zaczniemy od przygotowania danych testowych

{lang="text"}
~~~~~~~~
  object Data {
    val node1   = MachineNode("1243d1af-828f-4ba3-9fc0-a19d86852b5a")
    val node2   = MachineNode("550c4943-229e-47b0-b6be-3d686c5f013f")
    val managed = NonEmptyList(node1, node2)
  
    val time1: Epoch = epoch"2017-03-03T18:07:00Z"
    val time2: Epoch = epoch"2017-03-03T18:59:00Z" // +52 mins
    val time3: Epoch = epoch"2017-03-03T19:06:00Z" // +59 mins
    val time4: Epoch = epoch"2017-03-03T23:07:00Z" // +5 hours
  
    val needsAgents = WorldView(5, 0, managed, Map.empty, Map.empty, time1)
  }
  import Data._
~~~~~~~~

A> String interpolator `epoch` został napisany przy użyciu biblioteki [contextual](https://github.com/propensive/contextual) 
A> autorstwa Jona Pretty'iego. Biblioteka ta pozwala nam tworzyć obiekty ze stringów z weryfikacją na etapie kompilacji.
A>
A> {lang="text"}
A> ~~~~~~~~
A>   import java.time.Instant
A>   object EpochInterpolator extends Verifier[Epoch] {
A>     def check(s: String): Either[(Int, String), Epoch] =
A>       try Right(Epoch(Instant.parse(s).toEpochMilli))
A>       catch { case _ => Left((0, "not in ISO-8601 format")) }
A>   }
A>   implicit class EpochMillisStringContext(sc: StringContext) {
A>     val epoch = Prefix(EpochInterpolator, sc)
A>   }
A> ~~~~~~~~

Implementujemy algebry poprzez rozszerzenie interfejsów `Drone` i `Machines` podając konkretny kontekst monadyczny,
w najprostszym przypadku `Id`.

Nasza "mockowa" implementacja zwyczajnie odtwarza wcześniej przygotowany `WorldView`. 
Stan naszego systemu został wyizolowany, więc możemy użyć `var` do jego przechowywania:

{lang="text"}
~~~~~~~~
  class Mutable(state: WorldView) {
    var started, stopped: Int = 0
  
    private val D: Drone[Id] = new Drone[Id] {
      def getBacklog: Int = state.backlog
      def getAgents: Int = state.agents
    }
  
    private val M: Machines[Id] = new Machines[Id] {
      def getAlive: Map[MachineNode, Epoch] = state.alive
      def getManaged: NonEmptyList[MachineNode] = state.managed
      def getTime: Epoch = state.time
      def start(node: MachineNode): MachineNode = { started += 1 ; node }
      def stop(node: MachineNode): MachineNode = { stopped += 1 ; node }
    }
  
    val program = new DynAgentsModule[Id](D, M)
  }
~~~~~~~~

A> Powrócimy do tego kodu trochę później i zamienimy `var` na coś bezpieczniejszego.

Kiedy piszemy testy jednostkowe (używając `FlatSpec` z biblioteki Scalatest), tworzymy instancje `Mutable` 
i importujemy wszystkie jej pola i metody.

Nasze `drone` i `machines` używają `Id` jako kontekstu wykonania przez co interpretacja naszego programu
zwraca `Id[WoldView]` na którym możemy wykonywać asercje.

W tym trywialnym scenariuszy sprawdzamy czy `initial` zwraca tę sama wartość, której użyliśmy 
w naszej statycznej implementacji:

{lang="text"}
~~~~~~~~
  "Business Logic" should "generate an initial world view" in {
    val mutable = new Mutable(needsAgents)
    import mutable._
  
    program.initial shouldBe needsAgents
  }
~~~~~~~~

Możemy też stworzyć bardziej skomplikowane testy dla metod `update` i `act`,
które pomogą nam znaleźć błędy i dopracować wymagania:

{lang="text"}
~~~~~~~~
  it should "remove changed nodes from pending" in {
    val world = WorldView(0, 0, managed, Map(node1 -> time3), Map.empty, time3)
    val mutable = new Mutable(world)
    import mutable._
  
    val old = world.copy(alive = Map.empty,
                         pending = Map(node1 -> time2),
                         time = time2)
    program.update(old) shouldBe world
  }
  
  it should "request agents when needed" in {
    val mutable = new Mutable(needsAgents)
    import mutable._
  
    val expected = needsAgents.copy(
      pending = Map(node1 -> time1)
    )
  
    program.act(needsAgents) shouldBe expected
  
    mutable.stopped shouldBe 0
    mutable.started shouldBe 1
  }
~~~~~~~~

Przejście przez pełen komplet testów byłby dość nudny. Poniższe testy można łatwo zaimplementować używając tego 
samego podejścia:

- nie proś o nowych agentów gdy kolejka oczekujących jest niepusta
- nie wyłączaj agentów jeśli węzły są zbyt młode
- wyłącz agenty gdy backlog jest pusty a węzły wkrótce wygenerują nowe koszta
- nie wyłączaj agentów gdy obecne są oczekujące akcje
- wyłącz agenty gdy backlog jest pusty a ci są zbyt starzy
- wyłącz agenty nawet jeśli wykonują prace jeśli są zbyt starzy
- zignoruj nieodpowiadające oczekujące akcje podczas aktualizacji

Wszystkie te testy są synchroniczne i działają na wątku uruchamiającym testy (i mogą być uruchamiane równolegle).
Gdybyśmy zaprojektowali nasze testy z użyciem Akki, narażone byłyby na arbitralne timeouty a błędy ukryte byłyby 
w logach.

Ciężko jest przecenić zwiększenie produktywności wynikające z prostych testów logiki biznesowej. Weź pod uwagę, że
90% czasu programisty podczas interakcji z klientem poświęcone jest na ulepszanie, aktualizowanie i poprawianie 
tych właśnie reguł. Wszystko inne to tylko szczegół implementacyjny.


## Równolegle

Aplikacja którą stworzyliśmy uruchamia każdą z algebraicznych metod sekwencyjnie. Jednak jest kilka oczywistych miejsc
w których praca może być wykonywana równolegle.

### initial

W naszej definicji metody `initial` moglibyśmy zarządzać wszystkich informacji równocześnie zamiast wykonywać tylko jedno
zapytanie na raz.

W przeciwieństwie do metody `flatMap` która działa sekwencyjnie, Scalaz dostarcza składnie `Apply` 
przewidzianą do operacji równoległych:

{lang="text"}
~~~~~~~~
  ^^^^(D.getBacklog, D.getAgents, M.getManaged, M.getAlive, M.getTime)
~~~~~~~~

możemy również użyć notacji infiksowej (_infix_):

{lang="text"}
~~~~~~~~
  (D.getBacklog |@| D.getAgents |@| M.getManaged |@| M.getAlive |@| M.getTime)
~~~~~~~~

Jeśli każda z operacji równoległych zwraca ten sam kontekst, możemy wywołać funkcję w momencie gdy wszystkie one zwrócą
wynik. Przepiszmy `initial` aby skorzystać z tej możliwości:

{lang="text"}
~~~~~~~~
  def initial: F[WorldView] =
    ^^^^(D.getBacklog, D.getAgents, M.getManaged, M.getAlive, M.getTime) {
      case (db, da, mm, ma, mt) => WorldView(db, da, mm, ma, Map.empty, mt)
    }
~~~~~~~~


### act

W aktualnej implementacji `act` zatrzymujemy każdy z węzłów sekwencyjnie, czekając na wynik i kontynuując pracę 
dopiero gdy operacja się zakończy. Moglibyśmy jednak zatrzymać wszystkie węzły równolegle i na koniec zaktualizować
nasz obraz świata.

Wadą tego rozwiązania jest fakt, że błąd w którejkolwiek akcji spowoduje zwarcie zanim zdążymy zaktualizować pole
`pending`. Wydaje się to być rozsądnym kompromisem, gdyż nasza metoda `update` poradzi sobie z sytuacją w której
węzeł niespodziewanie się zatrzyma.

Potrzebujemy metody która operuje na typie `NonEmptyList` i pozwoli nam prze`map`ować każdy element na
`F[MachineNode]`, zwracając `F[NonEmptyList[MachineNode]]`. Metoda ta nazywa się `traverse`, a gdy na jej rezultacie 
wywołamy `flatMap` otrzymamy wartość typu `NonEmptyList[MachineNode]` z którą możemy sobie poradzić w prosty sposób:

{lang="text"}
~~~~~~~~
  for {
    stopped <- nodes.traverse(M.stop)
    updates = stopped.map(_ -> world.time).toList.toMap
    update = world.copy(pending = world.pending ++ updates)
  } yield update
~~~~~~~~

Prawdopodobnie wersja ta jest łatwiejsza do zrozumienia niż wersja sekwencyjna.


## Podsumowanie

1. *algebry* definiują interfejsy między systemami
2. *moduły* implementują algebry używając innych algebr
3. *interpretery* to konkretne implementacje algebr dla określonego `F[_]`
4. Interpretery testowe mogą zamienić części systemu wywołujące efekty uboczne, dając nam wysokie pokrycie testami.


# Dane i Funkcjonalności

Przychodząc ze świata obiektowego przyzwyczajeni jesteśmy do myślenia o danych i funkcjonalnościach jako jednym:
hierarchie klas zawierają metody a traity mogą wymagać obecności konkretnych pól (danych). Polimorfizm obiektu w czasie wykonania,
bazujący na relacji "jest" (_is a_), wymaga od klas aby dziedziczyły po wspólnym interfejsie. Rozwiązanie to może wywołać spory 
bałagan gdy tylko ilość naszego kodu zacznie się istotnie zwiększać. Proste struktury danych zostają przysłonięte setkami linii
kodu implementującego kolejne metody, traity które wmiksowujemy do naszych klas zaczynają cierpieć na problemy związane z kolejnością
inicjalizacji, a testowanie i mockowanie ściśle powiązanych komponentów staje się katorgą.

FP podchodzi inaczej do tego problemu, rozdzielając definicje funkcjonalności i danych. W tym rozdziale poznamy podstawowe
typy danych i zalety ograniczenia się do podzbioru funkcjonalności oferowanych przez Scalę. Odkryjemy również *typeklasy* 
jako sposób na osiągnięcie polimorfizmu już na etapie kompilacji: zaczniemy myśleć o strukturach danych w kategoriach relacji
"ma" (_has a_) zamiast "jest".


## Dane

Podstawowymi materiałami używanymi do budowania typów danych są:

- `final case class` znane również jako *produkty* (_products_)
- `sealed abstract class` znane również jako *koprodukty* (_coproducts_)
- `case object` oraz typy proste takie jak `Int`, `Double`, `String` to *wartości* (_values_)[^values]

[^values]: Chodzi tutaj o wartości na poziomie typów (_type level_). Dla przykładu: produktem na poziomie wartości (_value level_),
jest nowa wartość złożona z wielu wartości, np. `(1,2,3)`. Produktem na poziomie typów jest nowy typ złożony z wielu typów 
(czyli wartości na poziomie typów), np `(Int, String, Int)`. Może to wydawać się zawiłe ale nie ma potrzeby się tym przejmować.
Ważne jest aby zrozumieć że mamy 2 poziomy na których możemy definiować byty: poziom typów i poziom wartości, 
i że w tym wypadku mówimy o wartościach na poziomie typów.

z tym ograniczeniem, że nie mogą one mieć żadnych metod ani pól innych niż parametry konstruktora. Preferujemy
`abstract class` nad `trait` aby zyskać lepszą kompatybilność binarną i nie zachęcać do wmiksowywania traitów.

Wspólna nazwa dla *produktów*, *koproduktów* i *wartości* to *Algebraiczny Typ Danych*[^adt] (ADT).

[^adt]: _Algebraic Data Type_

Składamy typy danych analogicznie do algebra Boole’a opartej na operacjach `AND` i `XOR` (wykluczający `OR`):
produkt zawiera wszystkie typy z których się składa a koprodukt jest jednym z nich. Na przykład

-   produkt: `ABC = a AND b AND c`
-   koprodukt: `XYZ = x XOR y XOR z`

zapisane w Scali

{lang="text"}
~~~~~~~~
  // values
  case object A
  type B = String
  type C = Int
  
  // product
  final case class ABC(a: A.type, b: B, c: C)
  
  // coproduct
  sealed abstract class XYZ
  case object X extends XYZ
  case object Y extends XYZ
  final case class Z(b: B) extends XYZ
~~~~~~~~


### Rekursywne ADT

Kiedy ADT odnosi się to samego siebie przyjmuje nazwę *Rekursywny Algebraiczny Typ Danych*.

`scalaz.IList`, bezpieczna alternatywa dla typu `List` z biblioteki standardowej, jest rekursywna ponieważ
`ICons` zawiera referencje do `IList`.:

{lang="text"}
~~~~~~~~
  sealed abstract class IList[A]
  final case class INil[A]() extends IList[A]
  final case class ICons[A](head: A, tail: IList[A]) extends IList[A]
~~~~~~~~


### Funkcje w ADT

ADT mogą zawierać *czyste funkcje*

{lang="text"}
~~~~~~~~
  final case class UserConfiguration(accepts: Int => Boolean)
~~~~~~~~

Ale ADT, które zawierają funkcje nie są tak oczywiste jak mogłoby się wydawać, gdyż wyrażenie ich na JVMie
jest nieidealne. Dla przykładu, `Serializable`, `hashCode`, `equals` i `toString` nie zachowują się tak jak
byśmy się tego spodziewali.

Niestety, `Serializable` używany jest przez wiele frameworków mimo istnienia dużo lepszych alternatyw. Częstą
pułapką jest zapomnienie że `Serializable` może próbować zserializować całe domknięcie (_closure_) funkcji,
co może np. zabić produkcyjny serwer na którym aplikacja jest uruchomiona. Podobnymi problemami obciążone są inne typy Javowe
takie jak na przykład `Throwable`, który niesie w sobie referencje do arbitralnych obiektów.

Zbadamy dostępne alternatywy gdy pochylimy się nad biblioteka Scalaz w następnym rozdziale. Kosztem tych alternatyw będzie 
poświęcenie interoperacyjności (_interoperability_) z częścią ekosystemu Javy i Scali.


### Wyczerpywalność[^exhaustivity]

[^exhaustivity]: _Exhaustivity_

Istotne jest że definiując typy danych używamy konstrukcji `sealed abstract class`, a nie `abstract class`. Zapieczętowanie
(_sealing_) klasy oznacza że wszystkie podtypy (_subtypes_) muszą być zdefiniowane w tym samym pliku, pozwalając tym samym
kompilatorowi na sprawdzanie czy pattern matching jest wyczerpujący. Dodatkowo informacja ta może być wykorzystana przez makra
które pomagają nam eliminować boilerplate.

{lang="text"}
~~~~~~~~
  scala> sealed abstract class Foo
         final case class Bar(flag: Boolean) extends Foo
         final case object Baz extends Foo
  
  scala> def thing(foo: Foo) = foo match {
           case Bar(_) => true
         }
  <console>:14: error: match may not be exhaustive.
  It would fail on the following input: Baz
         def thing(foo: Foo) = foo match {
                               ^
~~~~~~~~

Jak widzimy kompilator jest w stanie pokazać deweloperowi co zostało zepsute gdy ten dodał nowy wariant do koproduktu
lub pominął już istniejący. Używamy tutaj flagi kompilatora `-Xfatal-warnings`, w innym przypadku błąd ten jest jedynie ostrzeżeniem.

Jednakże kompilator nie jest w stanie wykonać koniecznych sprawdzeń gdy klasa nie jest zapieczętowana lub gdy używamy 
dodatkowych ograniczeń (_guards_), np.:

{lang="text"}
~~~~~~~~
  scala> def thing(foo: Foo) = foo match {
           case Bar(flag) if flag => true
         }
  
  scala> thing(Baz)
  scala.MatchError: Baz (of class Baz$)
    at .thing(<console>:15)
~~~~~~~~

Aby zachować bezpieczeństwo, nie używaj ograniczeń na zapieczętowanych typach.

Nowa flaga, [`-Xstrict-patmat-analysis`](https://github.com/scala/scala/pull/5617), została zaproponowana aby dodatkowo
wzmocnić bezpieczeństwo pattern matchingu.


### Alternatywne Produkty i Koprodukty

Inną formą wyrażenia produktu jest tupla (krotka, ang. _tuple_), która przypomina finalną case klasę, ale pozbawioną etykiet.

`(A.type, B, C)` jest równoznaczna z `ABC` z wcześniejszego przykładu, ale do konstruowania ADT 
najlepiej używać jest klas, gdyż brak nazw jest kłopotliwe w praktyce. Dodatkowo case klasy są zdecydowanie bardziej wydajne
przy operowaniu na wartościach typów prostych (_primitive values_).

Inną formą wyrażenia koproduktu jest zagnieżdżanie typu `Either`, np.

{lang="text"}
~~~~~~~~
  Either[X.type, Either[Y.type, Z]]
~~~~~~~~

jest równoznaczny z zapieczętowaną klasą abstrakcyjną `XYZ`. Aby uzyskać czystszą składnię do definiowania zagnieżdżonych
typów `Either`, możemy zdefiniować alias typu zakończony dwukropkiem, co sprawi że używając notacji infiksowej będzie on wiązał
argument po prawej stronie jako pierwszy[^eitherright].

[^eitherright]: A więc `String |: Int |: Double` rozumiany jest jako `String |: (Int |: Double)` a nie `(String |: Int) |: Double`.

{lang="text"}
~~~~~~~~
  type |:[L,R] = Either[L, R]
  
  X.type |: Y.type |: Z
~~~~~~~~

Anonimowe koprodukty przydatne są gdy nie jesteśmy w stanie umieścić wszystkich typów w jednym pliku.

{lang="text"}
~~~~~~~~
  type Accepted = String |: Long |: Boolean
~~~~~~~~

Alternatywnym sposobem jest zdefiniowanie nowej zapieczętowanej klasy, której podtypy owijają potrzebne nam (zewnętrzne) typy.


{lang="text"}
~~~~~~~~
  sealed abstract class Accepted
  final case class AcceptedString(value: String) extends Accepted
  final case class AcceptedLong(value: Long) extends Accepted
  final case class AcceptedBoolean(value: Boolean) extends Accepted
~~~~~~~~

Pattern matching na tych formach koproduktów jest dość mozolny, dlatego też w Dottym (kompilator Scali następnej generacji) 
dostępne są [Unie](https://contributors.scala-lang.org/t/733) (_union types_). Istnieją również biblioteki (oparte o makra), 
takie jak [totalitarian](https://github.com/propensive/totalitarian) czy [iota](https://github.com/frees-io/iota),
które dostarczają kolejne sposoby na wyrażanie koproduktów.


### Przekazywanie Informacji

Typy danych, oprócz pełnienia funkcji kontenerów na kluczowe informacje biznesowe, pozwalają nam również
wyrażać ograniczenia dla tychże danych. Na przykład:

{lang="text"}
~~~~~~~~
  final case class NonEmptyList[A](head: A, tail: IList[A])
~~~~~~~~

nigdy nie będzie pusta. Sprawia to że `scalaz.NonEmptyList` jest użytecznym typem danych mimo tego, że
zawiera dokładnie te same dane jak `IList`.

Produkty często zawierają typy które są dużo bardziej ogólne niż powinno być dozwolone. W tradycyjnym podejściu
zorientowanym obiektowo moglibyśmy obsłużyć taki przypadek poprzez walidację danych za pomocą asercji:

{lang="text"}
~~~~~~~~
  final case class Person(name: String, age: Int) {
    require(name.nonEmpty && age > 0) // breaks Totality, don't do this!
  }
~~~~~~~~

Zamiast tego, możemy użyć typu `Either` i zwracać `Right[Person]` dla poprawnych instancji, zapobiegając tym samym przed
propagacją niepoprawnych instancji. Zauważ, że konstruktor jest prywatny: 

{lang="text"}
~~~~~~~~
  final case class Person private(name: String, age: Int)
  object Person {
    def apply(name: String, age: Int): Either[String, Person] = {
      if (name.nonEmpty && age > 0) Right(new Person(name, age))
      else Left(s"bad input: $name, $age")
    }
  }
  
  def welcome(person: Person): String =
    s"${person.name} you look wonderful at ${person.age}!"
  
  for {
    person <- Person("", -1)
  } yield welcome(person)
~~~~~~~~


#### Rafinowane Typy Danych[^refined]

[^refined]: _Refined Data Types_

Prostym sposobem ograniczenie zbioru możliwych wartości ogólnego typu jest użycie biblioteki [refined](https://github.com/fthomas/refined).
Aby zainstalować `refined` dodaj poniższą linie do pliku `build.sbt`.

{lang="text"}
~~~~~~~~
  libraryDependencies += "eu.timepit" %% "refined-scalaz" % "0.9.2"
~~~~~~~~

oraz poniższe importy

{lang="text"}
~~~~~~~~
  import eu.timepit.refined
  import refined.api.Refined
~~~~~~~~

`Refined` pozawala nam zdefiniować klasę `Person` używając rafinacji ad-hoc aby zapisać dokładne wymagania co do typu.
Rafinację taką wyrażamy jako `A Refined B`.

A> Wszystkie dwuparametrowe typy w Scali można zapisać w sposób inifksowy. Na przykład, `Either[String, Int]`
A> jest tym samym co `String Either Int`. Istnieje konwencja aby używać `Refined` w ten właśnie sposób,
A> gdyż `A Refined B` może być czytane jako "takie `A`, które spełnia wymagania zdefiniowane w `B`".

{lang="text"}
~~~~~~~~
  import refined.numeric.Positive
  import refined.collection.NonEmpty
  
  final case class Person(
    name: String Refined NonEmpty,
    age: Int Refined Positive
  )
~~~~~~~~

Dostęp do oryginalnej wartości odbywa się poprzez `.value`. Aby skonstruować instancje rafinowanego typu 
w czasie działa programu możemy użyć metody `.refineV`, która zwróci nam `Either`.

{lang="text"}
~~~~~~~~
  scala> import refined.refineV
  scala> refineV[NonEmpty]("")
  Left(Predicate isEmpty() did not fail.)
  
  scala> refineV[NonEmpty]("Sam")
  Right(Sam)
~~~~~~~~

Jeśli dodamy poniższy import

{lang="text"}
~~~~~~~~
  import refined.auto._
~~~~~~~~

możemy konstruować poprawne wartości z walidacją w czasie kompilacji

{lang="text"}
~~~~~~~~
  scala> val sam: String Refined NonEmpty = "Sam"
  Sam
  
  scala> val empty: String Refined NonEmpty = ""
  <console>:21: error: Predicate isEmpty() did not fail.
~~~~~~~~

Możemy również wyrażać bardziej skomplikowane wymagania, np za pomocą gotowej reguły `MaxSize` dostępnej
po dodaniu poniższych importów

{lang="text"}
~~~~~~~~
  import refined.W
  import refined.boolean.And
  import refined.collection.MaxSize
~~~~~~~~

Wyrażenie wymagania co do typu `String`, aby był jednocześnie niepusty i nie dłuższy niż 10 znaków:

{lang="text"}
~~~~~~~~
  type Name = NonEmpty And MaxSize[W.`10`.T]
  
  final case class Person(
    name: String Refined Name,
    age: Int Refined Positive
  )
~~~~~~~~

A> Notacja `W` jest skrótem od angielskiego "witness". Składania ta stanie się zdecydowanie prostsza w Scali 2.13,
A> która niesie wsparcie dla *typów literałowych* (_literal types_):
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   type Name = NonEmpty And MaxSize[10]
A> ~~~~~~~~

Łatwo jest zdefiniować własne ograniczenia które nie są dostępne bezpośrednio w bibliotece. Na przykład
w naszej aplikacji `drone-dynamic-agents` potrzebować będziemy sposobu aby upewnić się, że `String` jest wiadomością
zgodną z formatem `application/x-www-form-urlencoded`. Stwórzmy więc regułę używając wyrażeń regularnych:

{lang="text"}
~~~~~~~~
  sealed abstract class UrlEncoded
  object UrlEncoded {
    private[this] val valid: Pattern =
      Pattern.compile("\\A(\\p{Alnum}++|[-.*_+=&]++|%\\p{XDigit}{2})*\\z")
  
    implicit def urlValidate: Validate.Plain[String, UrlEncoded] =
      Validate.fromPredicate(
        s => valid.matcher(s).find(),
        identity,
        new UrlEncoded {}
      )
  }
~~~~~~~~


### Dzielenie się Jest Łatwe

Przez to, że ADT nie dostarczają żadnych funkcjonalności mają minimalny zbiór zależności. Sprawia to, że dzielenie 
tychże typów z innymi deweloperami jest nad wyraz łatwe. Używając prostego jeżyka modelowania danych, komunikacja oparta o kod, a 
nie specjalnie przygotowane dokumenty, staje się możliwa nawet wewnątrz zespołów interdyscyplinarnych (a więc 
składających się dodatkowo z np. administratorów baz danych, specjalistów od UI czy analityków biznesowych).

Dodatkowo, łatwiejsze staje się tworzenie narzędzi, które pomogą w konsumowaniu i produkowaniu
schematów danych dla innych języków danych albo łączeniu protokołów komunikacji.


### Wyliczanie Złożoności

Złożoność typu danych to liczba możliwych do stworzenia wartości. Dobry typ danych ma najmniejszą możliwą
złożoność, która pozwala mu przechować potrzebne informacje

Wartości mają wbudowaną złożoność:

-   `Unit` ma dokładnie jedną wartość (dlatego nazywa się "jednostką")
-   `Boolean` ma dwie wartości
-   `Int` ma 4,294,967,295 wartości
-   `String` ma efektywnie nieskończenie wiele wartości

Aby policzyć złożoność produktu wystarczy pomnożyć złożoności jego składowych.

-   `(Boolean, Boolean)` ma 4 wartości (`2*2`)
-   `(Boolean, Boolean, Boolean)` ma wartości (`2*2*2`)

Aby policzyć złożoność koproduktu sumujemy złożoności poszczególnych wariantów.

-   `(Boolean |: Boolean)` ma 4 wartości (`2+2`)
-   `(Boolean |: Boolean |: Boolean)` ma 6 wartości (`2+2+2`)

Aby określić złożoność ADT sparametryzowanego typem, mnożymy każdą z części przez złożoność parametru:

-   `Option[Boolean]` ma 3 wartości, `Some[Boolean]` i `None` (`2+1`)

W FP funkcje są *totalne* i muszą zwracać wartość dla każdego wejścia, bez *wyjątków*. Zmniejszanie złożoności
wejścia i wyjścia jest najlepszą droga do osiągnięcia totalności. Jako zasadę kciuk, przyjąć można, że funkcja jest
źle zaprojektowana jeśli złożoność jej wyjścia jest większa niż złożoność produktu jej wejść: w takim przypadku staje się 
ona źródłem entropii.

Złożoność funkcji totalnej jest liczbą możliwych funkcji które pasują do danej sygnatury typu (_type signature_): a więc
złożoność wyjścia do potęgi równej złożoności wejścia.

-   `Unit => Boolean` ma złożoność 2
-   `Boolean => Boolean` ma złożoność 4
-   `Option[Boolean] => Option[Boolean]` ma złożoność 27
-   `Boolean => Int` to zaledwie trylion kombinacji
-   `Int => Boolean` ma złożoność tak wielką, że gdyby każdej implementacji przypisać liczbę, to każda z tych liczb wymagałaby
4 gigabajtów pamięci aby ją zapisać

W rzeczywistości `Int => Boolean` będzie czymś tak trywialnym jak sprawdzenie parzystości lub rzadkie (_sparse_) 
wyrażenie zbioru bitów (`BitSet`). Funkcja taka w ADT powinna być raczej zastąpiona koproduktem istotnych funkcji.

Gdy nasza złożoność to "nieskończoność na wejściu, nieskończoność na wyjściu" powinniśmy wprowadzić bardziej restrykcyjne typy
i walidacje wejścia używając konstrukcji `Refined` wspomnianej w poprzedniej sekcji.

Zdolność do wyliczania złożoności sygnatury typu ma jeszcze jedno praktyczne zastosowanie:
możemy odszukać prostsze sygnatury przy pomocy matematyki na poziomie szkoły średniej! Aby przejść od sygnatury
do jej złożoności po prostu zamień

-   `Either[A, B]` na `a + b`
-   `(A, B)` na `a * b`
-   `A => B` na `b ^ a`

poprzestawiaj i zamień z powrotem. Dla przykładu, powiedzmy że zaprojektowaliśmy framework oparty na callbackach i 
dotarliśmy do miejsca, w którym potrzebujemy takiej sygnatury:

{lang="text"}
~~~~~~~~
  (A => C) => ((B => C) => C)
~~~~~~~~

Możemy ją przekonwertować i przetransformować

{lang="text"}
~~~~~~~~
  (c ^ (c ^ b)) ^ (c ^ a)
  = c ^ ((c ^ b) * (c ^ a))
  = c ^ (c ^ (a + b))
~~~~~~~~

a następnie zamienić z powrotem aby otrzymać

{lang="text"}
~~~~~~~~
  (Either[A, B] => C) => C
~~~~~~~~

która jest zdecydowanie prostsza: wystarczy że użytkownik dostarczy nam `Either[A, B] => C`.

Ta sama metoda może być użyta aby udowodnić, że

{lang="text"}
~~~~~~~~
  A => B => C
~~~~~~~~

jest równoznaczna z

{lang="text"}
~~~~~~~~
  (A, B) => C
~~~~~~~~

co znane jest jako  *Currying*.


### Preferuj koprodukty nad produkty

Archetypowym problemem, który pojawia się bardzo często, są wzajemnie wykluczające się parametry konfiguracyjne
`a`, `b` i `c`. Produkt `(a: Boolean, b: Boolean, c: Boolean)` ma złożoność równą 8, podczas gdy złożoność koproduktu

{lang="text"}
~~~~~~~~
  sealed abstract class Config
  object Config {
    case object A extends Config
    case object B extends Config
    case object C extends Config
  }
~~~~~~~~

to zaledwie 3. Lepiej jest zamodelować opisany scenariusz jako koprodukt niż pozwolić na wyrażenie 5 zupełnie nieprawidłowych
przypadków.

Złożoność typu danych wpływa również na testowanie kodu na nim opartego. Praktycznie niemożliwym jest przetestowanie wszystkich
możliwych wejść do funkcji, ale za to całkiem łatwo jest przetestować próbkę wartości za pomocą biblioteki do
testowania właściwości[^ppt] [Scalacheck](https://www.scalacheck.org/). Jeśli prawdopodobieństwo poprawności losowej próbki danych
jest niskie, jest to znak, że dane są niepoprawnie zamodelowane.

[^ppt]: _Property based testing_.


### Optymalizacja

Dużą zaletą używania jedynie podzbioru języka Scala do definiowania typów danych jest to, że narzędzia mogą 
optymalizować bytecode potrzebny do reprezentacji tychże.

Na przykład, możemy spakować pola typu `Boolean` i `Option` do tablicy bajtów, cache'ować wartości,
memoizować `hashCode`, optymalizować `equals`, używać wyrażeń `@switch` przy pattern matchingu i wiele wiele więcej.

Optymalizacje te nie mogą być zastosowane do hierarchii klas w stylu OOP, które to mogą przechowywać wewnętrzny stan,
rzucać wyjątki lub dostarczać doraźne implementacje metod.


## Funkcjonalności

Czyste funkcje są najczęściej definiowane jako metody wewnątrz obiektu (definicji typu `object`).

{lang="text"}
~~~~~~~~
  package object math {
    def sin(x: Double): Double = java.lang.Math.sin(x)
    ...
  }
  
  math.sin(1.0)
~~~~~~~~

Jednakże, używanie obiektów może być nieco niezręczne, gdyż wymaga od programisty czytania kodu od wewnątrz do zewnątrz
zamiast do lewej do prawej. Dodatkowo, funkcje z obiektu zawłaszczają przestrzeń nazw. Jeśli chcielibyśmy zdefiniować
funkcje `sin(t: T)` gdzieś indziej napotkalibyśmy błędy niejednoznacznych referencji (_ambigous reference_). Jest
to ten sam problem, który spotykamy w Javie gdy wybieramy między między metodami statycznymi i tymi definiowanymi w klasie.

W> Deweloperzy, którzy umieszczają swoje metody w `trait`ach, a następnie oczekują od użytkowników wmiksowywania tych traitów
W> w sposób znany jako *cake pattern*, po śmierci trafią prosto do piekła. Stanie się tak, ponieważ API zaimplementowane w
W> ten sposób ujawnia swoje szczegóły implementacyjne, rozdyma generowany bytecode, sprawia, że zachowanie kompatybilności 
W> binarnej jest praktycznie niemożliwe oraz myli funkcje autopodpowiadania w IDE.

Korzystając z konstrukcji `implicit class` (znanej również jako *extension methodology* lub *syntax*) i odrobiny boilerplate'u
możemy uzyskać znaną nam składnię:

{lang="text"}
~~~~~~~~
  scala> implicit class DoubleOps(x: Double) {
           def sin: Double = math.sin(x)
         }
  
  scala> (1.0).sin
  res: Double = 0.8414709848078965
~~~~~~~~

Często dobrze jest pominąć definiowanie obiektu i od razu sięgnąć po klasę niejawną (`implicit class`), ograniczając boilerplate do minimum:

{lang="text"}
~~~~~~~~
  implicit class DoubleOps(x: Double) {
    def sin: Double = java.lang.Math.sin(x)
  }
~~~~~~~~

A> `implicit class` to tak naprawdę jedynie wygodniejsza składnia do definiowania niejawnych konwersji (_implicit conversion_):
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit def DoubleOps(x: Double): DoubleOps = new DoubleOps(x)
A>   class DoubleOps(x: Double) {
A>     def sin: Double = java.lang.Math.sin(x)
A>   }
A> ~~~~~~~~
A> 
A> Co niestety ma swój koszt: za każdym razem gdy wywołujemy metodę dodaną w ten sposób tworzony i usuwany jest obiekt
A> klasy `DoubleOps`. Może to powodować zwiększony koszt sprzątania śmieci (_garbage collecting_).
A> 
A> Istnieje nieco bardziej rozwlekła forma klas niejawnych, która pozwala uniknąć zbędnej alokacji, przez co jest 
A> formą zalecaną:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit final class DoubleOps(private val x: Double) extends AnyVal {
A>     def sin: Double = java.lang.Math.sin(x)
A>   }
A> ~~~~~~~~


### Funkcje Polimorficzne

Bardziej popularnym rodzajem funkcji są funkcje polimorficzne, które żyją wewnątrz *typeklas*[^tc]. Typeklasa to trait, który:

- nie ma wewnętrznego stanu
- ma parametr typu
- ma przynajmniej jedną metodą abstrakcyjną (*kombinator prymitywny* (_primitive combinator_))
- może mieć metody uogólnione (*kombinatory pochodne* (_derived combinators_))
- może rozszerzać inne typeklasy

[^tc]: Ten potworek to spolszczona wersja słowa _typeclass_. Tłumaczenie tego terminu jako "klasa typu" jest rozwlekłe
i odbiegające dość daleko od wersji angielskiej. Zdecydowaliśmy się więc pozostać przy wersji angielskiej 
dostosowując jedynie pisownie... dla własnej wygody.

Dla każdego typu może istnieć tylko jedna instancja typeklasy, właściwość ta nazywa się *koherencją* lub *spójnością* *typeklas* (_typeclass coherence_).
Typeklasy mogą na pierwszy rzut oka wydawać się bardzo podobne do algebraicznych interfejsów, różnią się jednak tym że algebry
nie muszą zachowywać spójności.

A> W spójności typeklas chodzi głównie o konsekwencję, która zwiększa nasze zaufanie do niejawnych parametrów.
A> Analizowanie i rozumowanie na temat kodu, który zachowuje się inaczej zależnie od zaimportowanych symboli
A> byłoby bardzo trudne. Spójność typeklas w praktyce sprawia, że możemy uznać, że importy nie wpływają na zachowanie programu.
A> 
A> Dodatkowo, właściwość ta pozwala nam globalnie cache'ować niejawne wartości w czasie wykonania programu, 
A> oszczędzając tym samym pamięć i zwiększając wydajność poprzez zmniejszenie ilości pracy wykonywanej przez garbage collector.

Typeklasy używane się m.in w bibliotece standardowej Scali. Przyjrzymy się uproszczonej wersji `scala.math.Numeric`
aby zademonstrować zasadę działania tej konstrukcji:

{lang="text"}
~~~~~~~~
  trait Ordering[T] {
    def compare(x: T, y: T): Int
  
    def lt(x: T, y: T): Boolean = compare(x, y) < 0
    def gt(x: T, y: T): Boolean = compare(x, y) > 0
  }
  
  trait Numeric[T] extends Ordering[T] {
    def plus(x: T, y: T): T
    def times(x: T, y: T): T
    def negate(x: T): T
    def zero: T
  
    def abs(x: T): T = if (lt(x, zero)) negate(x) else x
  }
~~~~~~~~

Możemy zaobserwować wszystkie kluczowe cechy typeklasy w praktyce:

-   nie ma wewnętrznego stanu
-   `Ordering` i `Numeric` mają parametr typu `T`
-   `Ordering` definiuje abstrakcyjną metodą `compare` a `Numeric` metody `plus`, `times`, `negate` i `zero`
-   `Ordering` definiuje uogólnione `lt` i `gt` bazujące na `compare`, `Numeric` robi to samo z `abs` 
    bazując na `lt`, `negate` oraz `zero`
-   `Numeric` rozszerza `Ordering`

Możemy teraz napisać funkcję dla typów które "posiadają" instancję typeklasy `Numeric`:

{lang="text"}
~~~~~~~~
  def signOfTheTimes[T](t: T)(implicit N: Numeric[T]): T = {
    import N._
    times(negate(abs(t)), t)
  }
~~~~~~~~

Nie zależymy już od hierarchii klas w stylu OOP! Oznacza to że wejście do naszej funkcji nie musi być instancją typu 
`Numeric`, co jest niezwykle ważne kiedy chcemy zapewnić wsparcie dla klas zewnętrznych, których definicji nie jesteśmy
w stanie zmienić.

Inną zaletą typeklas jest to, że dane wiązane są z funkcjonalnościami na etapie kompilacji, a nie za pomocą dynamicznej 
dyspozycji (_dynamic dispatch_) w czasie działania programu, jak ma to miejsce w OOP.

Dla przykładu, tam gdzie klasa `List` może mieć tylko jedną implementację danej metody, używając typeklas możemy
używać różnych implementacji zależnie od typu elementów zawartych wewnątrz. Ty samym wykonujemy część pracy 
w czasie kompilacji zamiast zostawiać ją do czasu wykonania.


### Składnia

Składnia użyta to zapisania `signOfTheTimes` jest nieco niezgrabna, ale jest kilka rzeczy, które możemy poprawić. 

Użytkownicy chcieliby aby nasza metoda używała *wiązania kontekstu* (_context bounds_), ponieważ wtedy
sygnaturę można przeczytać w prost jako "przyjmuje `T`, dla którego istnieje `Numeric`"

{lang="text"}
~~~~~~~~
  def signOfTheTimes[T: Numeric](t: T): T = ...
~~~~~~~~

niestety, teraz musielibyśmy wszędzie używać `implicitly[Numeric[T]]`. Możemy pomóc sobie definiując
metodę pomocniczą w obiekcie towarzyszącym typeklasy

{lang="text"}
~~~~~~~~
  object Numeric {
    def apply[T](implicit numeric: Numeric[T]): Numeric[T] = numeric
  }
~~~~~~~~

aby uzyskać dostęp do jej instancji w bardziej zwięzły sposób

{lang="text"}
~~~~~~~~
  def signOfTheTimes[T: Numeric](t: T): T = {
    val N = Numeric[T]
    import N._
    times(negate(abs(t)), t)
  }
~~~~~~~~

Nadal jednak jest to, dla nas, implementatorów, problem. Zmuszeni jesteśmy używać czytanej od wewnątrz do zewnątrz składni
metod statycznych zamiast czytanej od lewej do prawej składni tradycyjnej. Możemy sobie z tym poradzić poprzez
definicję obiektu `ops` wewnątrz obiektu towarzyszącego typeklasy:

{lang="text"}
~~~~~~~~
  object Numeric {
    def apply[T](implicit numeric: Numeric[T]): Numeric[T] = numeric
  
    object ops {
      implicit class NumericOps[T](t: T)(implicit N: Numeric[T]) {
        def +(o: T): T = N.plus(t, o)
        def *(o: T): T = N.times(t, o)
        def unary_-: T = N.negate(t)
        def abs: T = N.abs(t)
  
        // duplicated from Ordering.ops
        def <(o: T): T = N.lt(t, o)
        def >(o: T): T = N.gt(t, o)
      }
    }
  }
~~~~~~~~

Zauważ, że zapis `-x` rozwijany jest przez kompilator do `x.unary_-`, dlatego też definiujemy rozszerzającą metodę (_extension method_)
`unary_-`. Możemy teraz zapisać naszą funkcję w sposób zdecydowanie czystszy:

{lang="text"}
~~~~~~~~
  import Numeric.ops._
  def signOfTheTimes[T: Numeric](t: T): T = -(t.abs) * t
~~~~~~~~

Dobra wiadomość jest taka, że nie musimy pisać całego tego boilerplatu własnoręcznie, ponieważ
[Simulacrum](https://github.com/mpilquist/simulacrum) dostarcza makro anotacje `@typeclass`, która automatycznie
generuje dla nas metodę `apply` i obiekt `ops`. Dodatkowo pozwala nam nawet zdefiniować alternatywne (zazwyczaj symboliczne)
nazwy dla metod. Całość:

{lang="text"}
~~~~~~~~
  import simulacrum._
  
  @typeclass trait Ordering[T] {
    def compare(x: T, y: T): Int
    @op("<") def lt(x: T, y: T): Boolean = compare(x, y) < 0
    @op(">") def gt(x: T, y: T): Boolean = compare(x, y) > 0
  }
  
  @typeclass trait Numeric[T] extends Ordering[T] {
    @op("+") def plus(x: T, y: T): T
    @op("*") def times(x: T, y: T): T
    @op("unary_-") def negate(x: T): T
    def zero: T
    def abs(x: T): T = if (lt(x, zero)) negate(x) else x
  }
  
  import Numeric.ops._
  def signOfTheTimes[T: Numeric](t: T): T = -(t.abs) * t
~~~~~~~~

Kiedy używamy operatora symbolicznego, możemy czytać (nazywać) go jak odpowiadającą mu metodę. Np. `<` przeczytamy
jako "less then",a nie "left angle bracket".

### Instancje

*Instancje* typu `Numeric` (które są również instancjami `Ordering`) są definiowane jako `implicit val` i rozszerzają
typeklasę, mogąc tym samym dostarczać bardziej optymalne implementacje uogólnionych metod:

{lang="text"}
~~~~~~~~
  implicit val NumericDouble: Numeric[Double] = new Numeric[Double] {
    def plus(x: Double, y: Double): Double = x + y
    def times(x: Double, y: Double): Double = x * y
    def negate(x: Double): Double = -x
    def zero: Double = 0.0
    def compare(x: Double, y: Double): Int = java.lang.Double.compare(x, y)
  
    // optimised
    override def lt(x: Double, y: Double): Boolean = x < y
    override def gt(x: Double, y: Double): Boolean = x > y
    override def abs(x: Double): Double = java.lang.Math.abs(x)
  }
~~~~~~~~

Mimo że używamy tutaj `+`, `*`, `unary_-`, `<` i `>`, które zdefiniowane są też przy użyciu `@ops` (i mogłyby spowodować
nieskończoną pętlę wywołań), są one również zdefiniowane bezpośrednio dla typu `Double`. Metody klasy są używane w 
pierwszej kolejności, a dopiero w przypadku ich braku kompilator szuka metod rozszerzających. W rzeczywistości kompilator Scali
obsługuje wywołania tych metod w specjalny sposób i zamienia je bezpośrednio na instrukcje bytecodu, odpowiednio `dadd`, `dmul`, `dcmpl` i `dcmpg`.

Możemy również zaimplementować `Numeric` dla Javowego `BigDecimal` (unikaj `scala.BigDecimal`, 
[jest całkowicie zepsuty](https://github.com/scala/bug/issues/9670)).

{lang="text"}
~~~~~~~~
  import java.math.{ BigDecimal => BD }
  
  implicit val NumericBD: Numeric[BD] = new Numeric[BD] {
    def plus(x: BD, y: BD): BD = x.add(y)
    def times(x: BD, y: BD): BD = x.multiply(y)
    def negate(x: BD): BD = x.negate
    def zero: BD = BD.ZERO
    def compare(x: BD, y: BD): Int = x.compareTo(y)
  }
~~~~~~~~

Możemy też zdefiniować nasz własny typ danych do reprezentowania liczb zespolonych:

{lang="text"}
~~~~~~~~
  final case class Complex[T](r: T, i: T)
~~~~~~~~

I uzyskać `Numeric[Complex[T]]` jeśli istnieje `Numeric[T]`. Instancje te zależą od typu `T` a więc definiujemy
je jako `def`, a nie `val`.

{lang="text"}
~~~~~~~~
  implicit def numericComplex[T: Numeric]: Numeric[Complex[T]] =
    new Numeric[Complex[T]] {
      type CT = Complex[T]
      def plus(x: CT, y: CT): CT = Complex(x.r + y.r, x.i + y.i)
      def times(x: CT, y: CT): CT =
        Complex(x.r * y.r + (-x.i * y.i), x.r * y.i + x.i * y.r)
      def negate(x: CT): CT = Complex(-x.r, -x.i)
      def zero: CT = Complex(Numeric[T].zero, Numeric[T].zero)
      def compare(x: CT, y: CT): Int = {
        val real = (Numeric[T].compare(x.r, y.r))
        if (real != 0) real
        else Numeric[T].compare(x.i, y.i)
      }
    }
~~~~~~~~

Uważny czytelnik zauważy, że `abs` jest czymś zupełnie innym niż oczekiwałby matematyk. Poprawna wartość zwracana
z tej metody powinna być typu `T` a nie `Complex[T]`.

`scala.math.Numeric` stara się robić zbyt wiele i nie uogólnia ponad liczby rzeczywiste. Pokazuje nam to, że 
małe, dobrze zdefiniowane typeklasy są często lepsze niż monolityczne kolekcje szczegółowych funkcjonalności.


### Niejawne rozstrzyganie[^implres]

[^implres] _Implicit resolution_

Wielokrotnie używaliśmy wartości niejawnych: ten rozdział ma na celu doprecyzować czym one są i jak tak na prawdę działają.

O *parametrach niejawnych* (_implicit parameters_) mówimy gdy metoda żąda aby unikalna instancja określonego typu znajdowała się
w *niejawnym zakresie* (_implicit scope_) wywołującego. Może do tego używać specjalnej składni ograniczeń kontekstu.
Parametry niejawne są dobrym sposobem na przekazywanie konfiguracji poprzez warstwy aplikacji.

W tym przykładzie `foo` wymaga aby dostępne były instancje typeklas `Numeric` i `Typeable` dla A, oraz instancja typu
`Handler`, który przyjmuje dwa parametry typu.

{lang="text"}
~~~~~~~~
  def foo[A: Numeric: Typeable](implicit A: Handler[String, A]) = ...
~~~~~~~~

*Konwersje niejawne* pojawiają się gdy używamy `implicit def`. Jednym z użyć niejawnych konwersji jest stosowanie 
metod rozszerzających. Kiedy kompilator próbuje odnaleźć metodę która ma zostać wywołana na obiekcie przegląda metody zdefiniowane 
w klasie tego obiektu a następnie wszystkie klasy po których ona dziedziczy (reguła podobna do tych znanych 
z Javy). Jeśli takie poszukiwanie się nie powiedzie kompilator zaczyna przeglądać *zakres niejawny* w poszukiwaniu
konwersji do innych typów, a następnie szuka wspomnianej metody zdefiniowanej na tychże typach.

Inny przykładem użycia niejawnych konwersji jest *derywacja typeklas* (_typeclass derivation_). W poprzednim rozdziale
napisaliśmy metodę niejawną która tworzyła instancję `Numeric[Complex[T]]` jeśli dostępna była instancja `Numeric[T]`.
Możemy w ten sposób łączyć wiele niejawnych metod (również rekurencyjnie), dochodząc tym samym do metody zwanej
"typeful programming", która pozwala nam wykonywać obliczenia na etapie kompilacji a nie w czasie działania programu.

Część która łączy niejawne parametry (odbiorców) z niejawnymi konwersjami i wartościami (dostawcami) nazywa się niejawnym rozstrzyganiem.

Najpierw w poszukiwaniu wartości niejawnych przeszukiwany jest standardowy zakres zmiennych, wg kolejności:

-   zakres lokalny, wliczając lokalne importy (np. ciało bloku lub metody)
-   zakres zewnętrzny, wliczając lokalne importy (np. ciało klasy)
-   przodkowie (np. ciało klasy po której dziedziczymy)
-   aktualny obiekt pakietu (_package object_)
-   obiekty pakietów nadrzędnych (kiedy używamy zagnieżdżonych pakietów)
-   importy globalne zdefiniowane w pliku

Jeśli nie uda się odnaleźć pasującej metody przeszukiwany jest zakres specjalny, składający się z:
wnętrza obiektu towarzyszącego danego typu, jego obiektu pakietu, obiektów pakietów zewnętrznych (jeśli jest zagnieżdżony)),
a w przypadku porażki to samo powtarzane jest dla typów po których nasza klasa dziedziczy. Operacje te wykonywane są kolejno dla:

-   typu zadanego parametru
-   oczekiwanego typu parametru
-   parametru typu (jeśli istnieje)

Jeśli w tej samej fazie poszukiwań znalezione zostaną dwie pasujące wartości niejawne, zgłaszany jest błąd niejednoznaczności
*ambigous implicit error*.

Wartości niejawne często definiowane są wewnątrz traitów, które następnie rozszerzane są przez obiekty. 
Praktyka ta podyktowana jest próbą kontrolowania priorytetów wg. których kompilator dobiera pasującą wartość, unikając
jednocześnie błędów niejednoznaczności.

Specyfikacja języka Scala jest dość nieprecyzyjna jeśli chodzi o przypadki skrajne, co sprawia że aktualna implementacja
kompilatora staje się standardem. Są pewne reguły, którymi będziemy się kierować w tej książce, jak na przykład
używanie `implicit val` zamiast `implicit object`, mimo że ta druga opcja jest bardziej zwięzła. [Kaprys 
kompilatora](https://github.com/scala/bug/issues/10411) sprawia, że wartości definiowane jako `implicit object` 
wewnątrz obiektu towarzyszącego są traktowane inaczej niż te definiowane za pomocą `implicit val`.

Niejawne rozstrzyganie zawodzi kiedy typeklasy tworzą hierarchię, tak jak w przypadku klas `Ordering` i `Numeric`.
Jeśli napiszemy funkcję, która przyjmuje niejawny parametr typu `Ordering` i zawołamy ją z typem prymitywnym, który
posiada instancję `Numeric` zdefiniowaną w obiekcie towarzyszącym typu `Numeric`, kompilator rzeczonej instancji nie znajdzie.

Niejawne rozstrzyganie staje się prawdziwą loterią gdy w grze [pojawią się aliasy typu](https://github.com/scala/bug/issues/10582)
które zmieniają *kształt* parametru. Dla przykładu, parametr niejawny używający aliasu `type Values[A] = List[Option[A]]` 
prawdopodobnie nie zostanie połączony z niejawną wartością zdefiniowaną dla typu  `List[Option[A]]` ponieważ kształt
zmienia się z *kolekcji kolekcji* elementów typu `A` na *kolekcję* elementów typu `A`.


## Modelowanie OAuth2

Zakończymy ten rozdział praktycznym przykładem modelowania danych i derywacji typeklas połączonych z projektowaniem
algebr i modułów, o którym mówiliśmy w poprzednim rozdziale.

W naszej aplikacje `drone-dynamic-agents` chcielibyśmy komunikować się z serwerem Drone i Google Cloud używając JSONa poprzez
REST. Obie usługi używają [OAuth2](https://tools.ietf.org/html/rfc6749) do uwierzytelniania użytkowników. Istnieje
wiele interpretacji OAuth2, ale my skupimy się na tej która działa z Google Cloud (wersja współpracująca z Drone 
jest jeszcze prostsza).


### Opis

Każda aplikacja komunikująca się z Google Cloud musi mieć skonfigurowany *Klucz Kliencki OAuth 2.0* (_OAuth 2.0 Client Key_) poprzez

{lang="text"}
~~~~~~~~
  https://console.developers.google.com/apis/credentials?project={PROJECT_ID}
~~~~~~~~

co da nam dostęp do *ID Klienta* (_Client ID_) oraz *Sekretu Klienta* (_Client secret_).

Aplikacja może wtedy uzyskać jednorazowy *kod* poprzez sprawienie aby użytkownik wykonał *Prośbę o Autoryzację* (_Authorization Request_)
w swojej przeglądarce (tak, naprawdę, **w swojej przeglądarce**). Musimy więc otworzyć poniższą stronę w przeglądarce:

{lang="text"}
~~~~~~~~
  https://accounts.google.com/o/oauth2/v2/auth?\
    redirect_uri={CALLBACK_URI}&\
    prompt=consent&\
    response_type=code&\
    scope={SCOPE}&\
    access_type=offline&\
    client_id={CLIENT_ID}
~~~~~~~~

*Kod* dostarczony zostanie pod `{CALLBACK_URI}` w postaci żądania `GET`. Aby go odebrać musimy posiadać serwer http
słuchający na interfejsie `localhost`.

Gdy zdobędziemy *kod*, możemy wykonać *Żądanie o Token Dostępu* (_Access Token Request_):

{lang="text"}
~~~~~~~~
  POST /oauth2/v4/token HTTP/1.1
  Host: www.googleapis.com
  Content-length: {CONTENT_LENGTH}
  content-type: application/x-www-form-urlencoded
  user-agent: google-oauth-playground
  code={CODE}&\
    redirect_uri={CALLBACK_URI}&\
    client_id={CLIENT_ID}&\
    client_secret={CLIENT_SECRET}&\
    scope={SCOPE}&\
    grant_type=authorization_code
~~~~~~~~

na które odpowiedzią będzie dokument JSON

{lang="text"}
~~~~~~~~
  {
    "access_token": "BEARER_TOKEN",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "REFRESH_TOKEN"
  }
~~~~~~~~

*Tokeny posiadacza* (_Bearer tokens_) zazwyczaj wygasają po godzinie i mogą być odświeżone poprzez
wykonanie kolejnego żądania http z użyciem *tokenu odświeżającego* (_refresh token_):

{lang="text"}
~~~~~~~~
  POST /oauth2/v4/token HTTP/1.1
  Host: www.googleapis.com
  Content-length: {CONTENT_LENGTH}
  content-type: application/x-www-form-urlencoded
  user-agent: google-oauth-playground
  client_secret={CLIENT_SECRET}&
    grant_type=refresh_token&
    refresh_token={REFRESH_TOKEN}&
    client_id={CLIENT_ID}
~~~~~~~~

na który odpowiedzią jest

{lang="text"}
~~~~~~~~
  {
    "access_token": "BEARER_TOKEN",
    "token_type": "Bearer",
    "expires_in": 3600
  }
~~~~~~~~

Wszystkie żądania do serwera powinny zwierać nagłówek

{lang="text"}
~~~~~~~~
  Authorization: Bearer BEARER_TOKEN
~~~~~~~~

z podstawioną rzeczywistą wartością `BEARER_TOKEN`.

Google wygasza wszystkie tokeny oprócz najnowszych 50, a więc czas odświeżania to tylko wskazówka. *Tokeny odświeżające*
trwają pomiędzy sesjami i mogą być wygaszone ręcznie przez użytkownika. Tak więc możemy mieć jednorazową aplikację
do pobierania tokenu odświeżającego, który następnie umieścimy w konfiguracji drugiej aplikacji.

Drone nie implementuje endpointu `/auth` ani tokenów odświeżających, a jedynie dostarcza `BEARER_TOKEN` poprzez
interfejs użytkownika.


### Dane

Pierwszym krokiem będzie zamodelowanie danych potrzebnych do implementacji OAuth2. Tworzymy więc ADT z dokładnie takimi
samymi polami jak te wymagane przez serwer OAuth2. Użyjemy typów `String` i `Long` dla zwięzłości, ale moglibyśmy użyć typów 
rafinowanych gdyby wyciekały one do naszego modelu biznesowego.

{lang="text"}
~~~~~~~~
  import refined.api.Refined
  import refined.string.Url
  
  final case class AuthRequest(
    redirect_uri: String Refined Url,
    scope: String,
    client_id: String,
    prompt: String = "consent",
    response_type: String = "code",
    access_type: String = "offline"
  )
  final case class AccessRequest(
    code: String,
    redirect_uri: String Refined Url,
    client_id: String,
    client_secret: String,
    scope: String = "",
    grant_type: String = "authorization_code"
  )
  final case class AccessResponse(
    access_token: String,
    token_type: String,
    expires_in: Long,
    refresh_token: String
  )
  final case class RefreshRequest(
    client_secret: String,
    refresh_token: String,
    client_id: String,
    grant_type: String = "refresh_token"
  )
  final case class RefreshResponse(
    access_token: String,
    token_type: String,
    expires_in: Long
  )
~~~~~~~~

W> Za wszelką cenę unikaj używania typu `java.net.URL`: wykonuje on zapytanie do serwera DNS 
W> gdy używamy `toString`, `equals` lub `hashCode`.
W>
W> Oprócz tego że jest to szalone i **bardzo bardzo** wolne, metody te mogą wyrzucić wyjątek I/O (nie są *czyste*) oraz
W> mogą zmieniać swoje zachowanie w zależności od konfiguracji sieciowej (nie są *deterministyczne*).
W>
W> Rafinowany typ `String Refined Url` pozwala nam porównywać instancje bazując na typie `String` oraz
W> w bezpieczny sposób budować `URL` jeśli wymaga od nas tego zastane API.
W>
W> Nie mniej, w kodzie który wymaga wysokiej wydajności wolelibyśmy zamienić `java.net.URL` na zewnętrzny parser
W> URLi, np. [jurl](https://github.com/anthonynsimon/jurl), gdyż nawet bezpieczne części `java.net.*` stają się
W> niezwykle wolne gdy używane są na dużą skalę.


### Funkcjonalność

Musimy przetransformować klasy zdefiniowane w poprzedniej sekcji do JSONa, URLi i formy znanej z żądań HTTP POST.
Ponieważ aby to osiągnąć niezbędny jest polimorfizm, potrzebować będziemy typeklas.

[`jsonformat`](https://github.com/scalaz/scalaz-deriving/tree/master/examples/jsonformat/src) to prosta biblioteka do 
pracy z JSONem, którą poznamy dokładniej w jednym z następnych rozdziałów. Została ona stworzona stawiając na 
pierwszym miejscu pryncypia programowania funkcyjnego i czytelność kodu. Składa się ona z AST do opisu JSONa 
oraz typeklas do jego kodowania i dekodowania:

{lang="text"}
~~~~~~~~
  package jsonformat
  
  sealed abstract class JsValue
  final case object JsNull                                    extends JsValue
  final case class JsObject(fields: IList[(String, JsValue)]) extends JsValue
  final case class JsArray(elements: IList[JsValue])          extends JsValue
  final case class JsBoolean(value: Boolean)                  extends JsValue
  final case class JsString(value: String)                    extends JsValue
  final case class JsDouble(value: Double)                    extends JsValue
  final case class JsInteger(value: Long)                     extends JsValue
  
  @typeclass trait JsEncoder[A] {
    def toJson(obj: A): JsValue
  }
  
  @typeclass trait JsDecoder[A] {
    def fromJson(json: JsValue): String \/ A
  }
~~~~~~~~

A> `\/` to wersja typu `Either` z biblioteki Scalaz, wyposażona w metodę `.flatMap`. Możemy używać tego typu
A> w konstrukcji `for`, podczas gdy `Either` umożliwia to dopiero od wersji Scali 2.12. Czytamy go jako 
*dysjunkcja" (_disjunction_) lub *wściekły zając* (_angry rabbit_).
A>
A> `scala.Either` został [dodany do biblioteki standardowej](https://issues.scala-lang.org/browse/SI-250) przez 
A> twórcę Scalaz, Tony'ego Morrisa, w 2017 roku. Typ `\/` został stworzony gdy
A> do typu `Either` dodane zostały niebezpieczne (_unsafe_) metody.

Potrzebować będziemy instancji `JsDecoder[AccessResponse]` i `JsDecoder[RefreshResponse]`. Możemy je zbudować
używają funkcji pomocniczej:

{lang="text"}
~~~~~~~~
  implicit class JsValueOps(j: JsValue) {
    def getAs[A: JsDecoder](key: String): String \/ A = ...
  }
~~~~~~~~

Umieścimy je w obiektach towarzyszących naszych typów danych aby zawsze znajdowały się w zakresie niejawnym:

{lang="text"}
~~~~~~~~
  import jsonformat._, JsDecoder.ops._
  
  object AccessResponse {
    implicit val json: JsDecoder[AccessResponse] = j =>
      for {
        acc <- j.getAs[String]("access_token")
        tpe <- j.getAs[String]("token_type")
        exp <- j.getAs[Long]("expires_in")
        ref <- j.getAs[String]("refresh_token")
      } yield AccessResponse(acc, tpe, exp, ref)
  }
  
  object RefreshResponse {
    implicit val json: JsDecoder[RefreshResponse] = j =>
      for {
        acc <- j.getAs[String]("access_token")
        tpe <- j.getAs[String]("token_type")
        exp <- j.getAs[Long]("expires_in")
      } yield RefreshResponse(acc, tpe, exp)
  }
~~~~~~~~

Możemy teraz sparsować ciąg znaków do typu `AccessResponse` lub `RefreshResponse`

{lang="text"}
~~~~~~~~
  scala> import jsonformat._, JsDecoder.ops._
  scala> val json = JsParser("""
                       {
                         "access_token": "BEARER_TOKEN",
                         "token_type": "Bearer",
                         "expires_in": 3600,
                         "refresh_token": "REFRESH_TOKEN"
                       }
                       """)
  
  scala> json.map(_.as[AccessResponse])
  AccessResponse(BEARER_TOKEN,Bearer,3600,REFRESH_TOKEN)
~~~~~~~~

Musimy stworzyć nasze własne typeklasy do kodowania danych w postaci URLi i żądań POST. 
Poniżej widzimy całkiem rozsądny design:

{lang="text"}
~~~~~~~~
  // URL query key=value pairs, in un-encoded form.
  final case class UrlQuery(params: List[(String, String)])
  
  @typeclass trait UrlQueryWriter[A] {
    def toUrlQuery(a: A): UrlQuery
  }
  
  @typeclass trait UrlEncodedWriter[A] {
    def toUrlEncoded(a: A): String Refined UrlEncoded
  }
~~~~~~~~

Musimy zapewnić instancje dla typów podstawowych:

{lang="text"}
~~~~~~~~
  import java.net.URLEncoder
  
  object UrlEncodedWriter {
    implicit val encoded: UrlEncodedWriter[String Refined UrlEncoded] = identity
  
    implicit val string: UrlEncodedWriter[String] =
      (s => Refined.unsafeApply(URLEncoder.encode(s, "UTF-8")))
  
    implicit val long: UrlEncodedWriter[Long] =
      (s => Refined.unsafeApply(s.toString))
  
    implicit def ilist[K: UrlEncodedWriter, V: UrlEncodedWriter]
      : UrlEncodedWriter[IList[(K, V)]] = { m =>
      val raw = m.map {
        case (k, v) => k.toUrlEncoded.value + "=" + v.toUrlEncoded.value
      }.intercalate("&")
      Refined.unsafeApply(raw) // by deduction
    }
  
  }
~~~~~~~~

Używamy `Refined.unsafeApply` kiedy jesteśmy pewni, że zawartość stringa jest już poprawnie zakodowana i możemy 
pominąć standardową weryfikację.

`ilist` jest przykładem prostej derywacji typeklasy, w sposób podobny do tego którego użyliśmy przy `Numeric[Complex]`.
Metoda `.intercalate` to bardziej ogólna wersja `.mkString`.

A> `UrlEncodedWriter` wykorzystuje funkcjonalność *Pojedynczych Metod Abstrakcyjnych_* (SAM, _Single Abstract Method_) 
A> języka Scala. Pełna forma powyższego zapisu to:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit val string: UrlEncodedWriter[String] =
A>     new UrlEncodedWriter[String] {
A>       override def toUrlEncoded(s: String): String = ...
A>     }
A> ~~~~~~~~
A> 
A> Kiedy kompilator Scali oczekuje klasy, która posiada jedną metodę abstrakcyjną, a otrzyma lambdę, dopisuje on 
A> automatycznie niezbędny boilerplate.
A> 
A> Zanim funkcjonalność ta została dodana, powszechnym było definiowanie metody `instance` w obiekcie
A> towarzyszącym typeklasy
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def instance[T](f: T => String): UrlEncodedWriter[T] =
A>     new UrlEncodedWriter[T] {
A>       override def toUrlEncoded(t: T): String = f(t)
A>     }
A> ~~~~~~~~
A> 
A> pozwalającej na
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit val string: UrlEncodedWriter[String] = instance { s => ... }
A> ~~~~~~~~
A> 
A> Praktyka ta nadal stosowana jest w projektach, które muszą wspierać starsze wersje języka lub dla typeklas
A> które wymagają więcej niż jednej metody.
A> 
A> Warto pamiętać że istnieje wiele błędów związanych z typami SAM, ponieważ nie współdziałają one ze wszystkimi 
A> funkcjonalnościami języka. Jeśli napotkasz dziwne błędy kompilatora, warto zrezygnować z tego udogodnienia.

W rozdziale poświęconym *Derywacji Typeklas* pokażemy jak stworzyć instancję `UrlQueryWriter` automatycznie, oraz jak
oczyścić nieco kod, który już napisaliśmy. Na razie jednak napiszmy boilerplate dla typów których chcemy używać:

{lang="text"}
~~~~~~~~
  import UrlEncodedWriter.ops._
  object AuthRequest {
    implicit val query: UrlQueryWriter[AuthRequest] = { a =>
      UrlQuery(List(
        ("redirect_uri"  -> a.redirect_uri.value),
        ("scope"         -> a.scope),
        ("client_id"     -> a.client_id),
        ("prompt"        -> a.prompt),
        ("response_type" -> a.response_type),
        ("access_type"   -> a.access_type))
    }
  }
  object AccessRequest {
    implicit val encoded: UrlEncodedWriter[AccessRequest] = { a =>
      List(
        "code"          -> a.code.toUrlEncoded,
        "redirect_uri"  -> a.redirect_uri.toUrlEncoded,
        "client_id"     -> a.client_id.toUrlEncoded,
        "client_secret" -> a.client_secret.toUrlEncoded,
        "scope"         -> a.scope.toUrlEncoded,
        "grant_type"    -> a.grant_type.toUrlEncoded
      ).toUrlEncoded
    }
  }
  object RefreshRequest {
    implicit val encoded: UrlEncodedWriter[RefreshRequest] = { r =>
      List(
        "client_secret" -> r.client_secret.toUrlEncoded,
        "refresh_token" -> r.refresh_token.toUrlEncoded,
        "client_id"     -> r.client_id.toUrlEncoded,
        "grant_type"    -> r.grant_type.toUrlEncoded
      ).toUrlEncoded
    }
  }
~~~~~~~~


### Moduł

Tym samym zakończyliśmy modelowanie danych i funkcjonalności niezbędnych do implementacji protokołu OAuth2.
Przypomnij sobie z poprzedniego rozdziału, że komponenty które interagują ze światem zewnętrznym definiujemy jako algebry,
a logikę biznesową jako moduły, aby pozwolić na gruntowne jej przetestowanie.

Definiujemy algebry, na których bazujemy oraz używamy ograniczeń kontekstu aby pokazać, że nasze odpowiedzi muszą posiadać
instancję `JsDecoder`, a żądania `UrlEncodedWriter`:

{lang="text"}
~~~~~~~~
  trait JsonClient[F[_]] {
    def get[A: JsDecoder](
      uri: String Refined Url,
      headers: IList[(String, String)]
    ): F[A]
  
    def post[P: UrlEncodedWriter, A: JsDecoder](
      uri: String Refined Url,
      payload: P,
      headers: IList[(String, String]
    ): F[A]
  }
~~~~~~~~

Zauważ że definiujemy jedynie szczęśliwy scenariusz w API klasy `JsonClient`. Obsługą błędów zajmiemy się w jednym
z kolejnych rozdziałów.

Pozyskanie `CodeToken` z serwera Google `OAuth2` wymaga

1. wystartowania serwera HTTP na lokalnej maszynie i odczytanie numeru portu na którym nasłuchuje.
2. otworzenia strony internetowej w przeglądarce użytkownika, tak aby mógł zalogować się do usług Google swoimi danymi
i uwierzytelnić aplikacje
3. przechwycenia kodu, poinformowania użytkownika o następnych krokach i zamknięcia serwera HTTP.

Możemy zamodelować to jako trzy metody wewnątrz algebry `UserInteraction`.

{lang="text"}
~~~~~~~~
  final case class CodeToken(token: String, redirect_uri: String Refined Url)
  
  trait UserInteraction[F[_]] {
    def start: F[String Refined Url]
    def open(uri: String Refined Url): F[Unit]
    def stop: F[CodeToken]
  }
~~~~~~~~

Ujęte w ten sposób wydaje się to niemal proste.

Potrzebujemy również algebry pozwalającej abstrahować nam nad lokalnym czasem systemu

{lang="text"}
~~~~~~~~
  trait LocalClock[F[_]] {
    def now: F[Epoch]
  }
~~~~~~~~

oraz typu danych, którego użyjemy w implementacji logiki odświeżania tokenów

{lang="text"}
~~~~~~~~
  final case class ServerConfig(
    auth: String Refined Url,
    access: String Refined Url,
    refresh: String Refined Url,
    scope: String,
    clientId: String,
    clientSecret: String
  )
  final case class RefreshToken(token: String)
  final case class BearerToken(token: String, expires: Epoch)
~~~~~~~~

Możemy teraz napisać nasz moduł klienta OAuth2:

{lang="text"}
~~~~~~~~
  import http.encoding.UrlQueryWriter.ops._
  
  class OAuth2Client[F[_]: Monad](
    config: ServerConfig
  )(
    user: UserInteraction[F],
    client: JsonClient[F],
    clock: LocalClock[F]
  ) {
    def authenticate: F[CodeToken] =
      for {
        callback <- user.start
        params   = AuthRequest(callback, config.scope, config.clientId)
        _        <- user.open(params.toUrlQuery.forUrl(config.auth))
        code     <- user.stop
      } yield code
  
    def access(code: CodeToken): F[(RefreshToken, BearerToken)] =
      for {
        request <- AccessRequest(code.token,
                                 code.redirect_uri,
                                 config.clientId,
                                 config.clientSecret).pure[F]
        msg     <- client.post[AccessRequest, AccessResponse](
                     config.access, request)
        time    <- clock.now
        expires = time + msg.expires_in.seconds
        refresh = RefreshToken(msg.refresh_token)
        bearer  = BearerToken(msg.access_token, expires)
      } yield (refresh, bearer)
  
    def bearer(refresh: RefreshToken): F[BearerToken] =
      for {
        request <- RefreshRequest(config.clientSecret,
                                  refresh.token,
                                  config.clientId).pure[F]
        msg     <- client.post[RefreshRequest, RefreshResponse](
                     config.refresh, request)
        time    <- clock.now
        expires = time + msg.expires_in.seconds
        bearer  = BearerToken(msg.access_token, expires)
      } yield bearer
  }
~~~~~~~~


## Podsumowanie

-  *algebraiczne typy danych* (ADT) są definiowane jako *produkty* (`final case class`) i *koprodukty* 
    (`sealed abstract class`).
-   Typy `Refined` egzekwują ograniczenia na zbiorze możliwych wartości.
-   konkretne funkcje mogą być definiowane wewnątrz klas niejawnych (`implicit class`) aby zachować
    kierunek czytania od lewej do prawej.
-   funkcje polimorficzne definiowane są wewnątrz *typeklas*. Funkcjonalności zapewniane są poprzez 
    *ograniczenia kontekstu* wyrażające relacje "ma", a nie hierarchie klas wyrażające relacje "jest".
-   anotacja `@simulacrum.typeclass` generuje obiekt `.ops` wewnątrz towarzysza typu, zapewniając wygodniejszą
    składnię dla metod typeklasy.
-   *derywacja typeklas* to kompozycja typeklas odbywająca się w czasie kompilacji.


# Typeklasy ze Scalaz

W tym rozdziale przejdziemy przez niemal wszystkie typeklasy zdefiniowane w `scalaz-core`. Nie wszystkie z nich
znajdują zastosowanie w `drone-dynamic-agents` więc czasami będziemy używać samodzielnych przykładów.

Napotkać można krytykę w stosunku do konwencji nazewniczych stosowanych w Scalaz i programowaniu funkcyjnym w 
ogólności. Większość nazw podąża za konwencjami wprowadzonymi w Haskellu, który bazował z kolei na dziale matematyki
zwanym *Teorią Kategorii*. Możesz śmiało użyć aliasów typów, jeśli uważasz że rzeczowniki pochodzące od 
głównej funkcjonalności są łatwiejsze do zapamiętania (np. `Mappable`, `Pureable`, `FlatMappable`).

Zanim wprowadzimy hierarchię typeklas, popatrzmy na cztery metody, które są najistotniejsze z punkty widzenia
kontroli przepływu. Metod tych używać będziemy w większości typowych aplikacji funkcyjnych:

| Typeklasa     | Metoda     | Z         | Mając dane  | Do        |
|-------------- |----------- |---------- |------------ |---------- |
| `Functor`     | `map`      | `F[A]`    | `A => B`    | `F[B]`    |
| `Applicative` | `pure`     | `A`       |             | `F[A]`    |
| `Monad`       | `flatMap`  | `F[A]`    | `A => F[B]` | `F[B]`    |
| `Traverse`    | `sequence` | `F[G[A]]` |             | `G[F[A]]` |

Wiemy, że operacje zwracające `F[_]` mogą być wykonywane sekwencyjnie wewnątrz konstrukcji `for` przy użyciu
metody `.flatMap`, która zdefiniowana jest wewnątrz `Monad[F]`. Możemy myśleć o `F[A]` jak o kontenerze na 
pewien *efekt*, którego rezultatem jest wartość typu `A`. `.flatMap` pozwala nam wygenerować nowe efekty `F[B]` 
na podstawie rezultatów wykonania wcześniejszych efektów.

Oczywiście nie wszystkie konstruktory typu `F[_]` wiążą się z efektami ubocznymi, nawet jeśli maja instancję
`Monad[F]`, często są to po prostu struktury danych. Używając najmniej konkretnej (czyli najbardziej ogólnej) abstrakcji
możemy w łatwy sposób współdzielić kod operujący na typach `List`, `Either`, `Future` i wielu innych.

Jeśli jedyne czego potrzebujemy to przetransformować wynik `F[_]`, wystarczy że użyjemy metody `map`, definiowanej w 
typeklasie `Functor`. W rozdziale 3 uruchamialiśmy efekty równolegle, tworząc produkt i mapując go. W Programowaniu
Funkcyjnym obliczenia wykonywane równolegle są uznawane za **słabsze** niż te wykonywane sekwencyjnie.

Pomiędzy `Monad`ą i `Functor`em leży `Applicative`, która definiuje metodę `pure` pozwalającą nam wynosić (_lift_)
wartości do postaci efektów lub tworzyć struktury danych z pojedynczych wartości. 

`.sequence` jest użyteczna gdy chcemy poprzestawiać konstruktory typów. Gdy mamy `F[G[_]]` a potrzebujemy `G[F[_]]`,
np. zamiast `List[Future[Int]]` potrzebujemy `Future[List[Int]]`, wtedy użyjemy `.sequence`.


## Plan

Ten rozdział jest dłuższy niż zazwyczaj i wypełniony po brzegi informacjami. Przejście przez niego w wielu podejściach 
jest czymś zupełnie normalnym. Zapamiętanie  wszystkiego wymagałoby supermocy, więc potraktuj go raczej jako miejsce do którego
możesz wracać gdy będziesz potrzebował więcej informacji.

Pominięte zostały typeklasy, które rozszerzają typ `Monad`, dostały one swój własny rozdział.

Scalaz używa generacji kodu, ale nie za pomocą simulacrum. Niemniej dla zwięzłości prezentujemy przykłady bazujące na 
anotacji `@typeclass`. Równoznaczna składanie dostępna jest gdy zaimportujemy `scalaz._` i `Scalaz._` a jej 
implementacja znajduje się w pakiecie `scalaz.syntax` w kodzie źródłowym scalaz.

{width=100%}
![](images/scalaz-core-tree.png)

{width=60%}
![](images/scalaz-core-cliques.png)

{width=60%}
![](images/scalaz-core-loners.png)


## Rzeczy Złączalne[^appendables]

[^appendables]: _Appendable Things_

{width=25%}
![](images/scalaz-semigroup.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Semigroup[A] {
    @op("|+|") def append(x: A, y: =>A): A
  
    def multiply1(value: F, n: Int): F = ...
  }
  
  @typeclass trait Monoid[A] extends Semigroup[A] {
    def zero: A
  
    def multiply(value: F, n: Int): F =
      if (n <= 0) zero else multiply1(value, n - 1)
  }
  
  @typeclass trait Band[A] extends Semigroup[A]
~~~~~~~~

A> `|+|` znany jest jako operator "TIE Fighter"[^tiefighter]. Istnieje również bardzo ekscytujący "Advanced TIE
A> Fighter", który omówimy w następnej sekcji.

[^tiefighter]: [Myśliwiec TIE](https://pl.wikipedia.org/wiki/TIE_fighter)

`Semigroup` (półgrupa) może być zdefiniowana dla danego typu jeśli możemy połączyć ze sobą dwie jego wartości. Operacja ta
musi być *łączna* (_associative_), co oznacza, że kolejność zagnieżdżonych operacji nie powinna mieć znaczenia, np:

{lang="text"}
~~~~~~~~
  (a |+| b) |+| c == a |+| (b |+| c)
  
  (1 |+| 2) |+| 3 == 1 |+| (2 |+| 3)
~~~~~~~~

`Monoid` jest półgrupą z elementem *zerowym* (_zero_) (również zwanym elementem *pustym* (_empty_), *tożsamym* (_identity_) lub *neutralnym*).
Połączenie `zero` z dowolną inną wartością `a` powinno zwracać to samo, niezmienione `a`.

{lang="text"}
~~~~~~~~
  a |+| zero == a
  
  a |+| 0 == a
~~~~~~~~

Prawdopodobnie przywołaliśmy tym samym wspomnienie typu `Numeric` z Rozdziału 4. Istnieją implementacje typeklasy
`Monoid` dla wszystkich prymitywnych typów liczbowych, ale koncepcja rzeczy *złączalnych* jest użyteczna również
dla typów inne niż te liczbowe.

{lang="text"}
~~~~~~~~
  scala> "hello" |+| " " |+| "world!"
  res: String = "hello world!"
  
  scala> List(1, 2) |+| List(3, 4)
  res: List[Int] = List(1, 2, 3, 4)
~~~~~~~~

`Band` (pas) dodaje prawo, gwarantujące, że `append` wywołane na dwóch takich samych elementach jest
*idempotentne*, tzn. zwraca tą samą wartość. Przykładem są typy, które mają tylko jedną wartość, takie jak `Unit`,
kresy górne (_least upper bound_), lub zbiory (`Set`). `Band` nie wnosi żadnych dodatkowych metod, ale użytkownicy 
mogą wykorzystać dodatkowe gwarancje do optymalizacji wydajności.

A> Viktor Klang z Lightbendu proponuje frazę
A> [effectively-once delivery](https://twitter.com/viktorklang/status/789036133434978304) dla przetwarzania wiadomości 
A> za pomocą idempotentnych operacji, takich jak `Band.append`.

Jako realistyczny przykład dla `Monoid`u, rozważmy system transakcyjny, który posiada ogromną bazę reużywalnych
wzorów transakcji. Wypełnianie wartości domyślnych dla nowej transakcji wymaga wybrania i połączenia wielu 
wzorów, z zasadą "ostatni wygrywa" jeśli dwa wzory posiadają wartości dla tego samego pola. "Wybieranie" jest
wykonywane dla nas przez osobny system, a naszym zadaniem jest jedynie połączyć wzory według kolejności.

Stworzymy prosty schemat aby zobrazować zasadę działania, pamiętając przy tym, że prawdziwy system oparty byłby na dużo bardziej
skomplikowanym ADT.

{lang="text"}
~~~~~~~~
  sealed abstract class Currency
  case object EUR extends Currency
  case object USD extends Currency
  
  final case class TradeTemplate(
    payments: List[java.time.LocalDate],
    ccy: Option[Currency],
    otc: Option[Boolean]
  )
~~~~~~~~

Jeśli chcemy napisać metodę, która przyjmuje parametr `templates: List[TradeTemplate]`, wystarczy że zawołamy

{lang="text"}
~~~~~~~~
  val zero = Monoid[TradeTemplate].zero
  templates.foldLeft(zero)(_ |+| _)
~~~~~~~~

i gotowe!

Jednak aby móc zawołać `zero` lub `|+|` musimy mieć dostęp do instancji `Monoid[TradeTemplate]`. Chociaż 
w ostatnim rozdziale zobaczymy jak wyderywować taką instancję w sposób generyczny, na razie stworzymy ją ręcznie:  

{lang="text"}
~~~~~~~~
  object TradeTemplate {
    implicit val monoid: Monoid[TradeTemplate] = Monoid.instance(
      (a, b) => TradeTemplate(a.payments |+| b.payments,
                              a.ccy |+| b.ccy,
                              a.otc |+| b.otc),
      TradeTemplate(Nil, None, None)
    )
  }
~~~~~~~~

Jednak nie jest to do końca to czego byśmy chcieli, gdyż `Monoid[Option[A]]` łączy ze sobą wartości wewnętrzne, np.

{lang="text"}
~~~~~~~~
  scala> Option(2) |+| None
  res: Option[Int] = Some(2)
  scala> Option(2) |+| Option(1)
  res: Option[Int] = Some(3)
~~~~~~~~

podczas gdy my chcielibyśmy zachowania "ostatni wygrywa". Możemy więc nadpisać domyślną instancję `Monoid[Option[A]]` 
naszą własną:

{lang="text"}
~~~~~~~~
  implicit def lastWins[A]: Monoid[Option[A]] = Monoid.instance(
    {
      case (None, None)   => None
      case (only, None)   => only
      case (None, only)   => only
      case (_   , winner) => winner
    },
    None
  )
~~~~~~~~

Wszystko kompiluje się poprawnie, więc wypróbujmy nasze dzieło...

{lang="text"}
~~~~~~~~
  scala> import java.time.{LocalDate => LD}
  scala> val templates = List(
           TradeTemplate(Nil,                     None,      None),
           TradeTemplate(Nil,                     Some(EUR), None),
           TradeTemplate(List(LD.of(2017, 8, 5)), Some(USD), None),
           TradeTemplate(List(LD.of(2017, 9, 5)), None,      Some(true)),
           TradeTemplate(Nil,                     None,      Some(false))
         )
  
  scala> templates.foldLeft(zero)(_ |+| _)
  res: TradeTemplate = TradeTemplate(
                         List(2017-08-05,2017-09-05),
                         Some(USD),
                         Some(false))
~~~~~~~~

Jedyne co musieliśmy zrobić to zdefiniować jeden mały kawałek logiki biznesowej, a całą resztę
zrobił za nas `Monoid`!

Zauważ, że listy płatności są łączone. Dzieje się tak ponieważ domyślny `Monoid[List]` zachowuje się ten właśnie sposób.
Gdyby wymagania biznesowe były inne, wystarczyłoby dostarczyć inną instancję `Monoid[List[LocalDate]]`. Przypomnij sobie
z Rozdziału 4, że dzięki polimorfizmowi czasu kompilacji możemy zmieniać zachowanie `append` dla `List[E]` w zależności
od `E`, a nie tylko od implementacji `List`.

A> Kiedy w Rozdziale 4 wprowadzaliśmy typeklasy, powiedzieliśmy że dla danego typu może istnieć tylko jedna instancja,
A> a więc w aplikacji istnieje tylko jeden `Monoid[Option[Boolean]]`. *Osierocone instancje* (_orphan instances_), takie jak 
A> `lastWins` to najprostsza droga do zaburzenia spójności.
A>
A> Moglibyśmy uzasadniać lokalne zaburzenie spójności zamieniając dostęp do `lastwins` na prywatny, ale gdy dojdziemy do 
A> typeklasy `Plus` zobaczymy lepszy sposób na implementację naszego monoidu. Kiedy dotrzemy do typów tagowanych, zobaczymy
A> sposób jeszcze lepszy: użycie `LastOption` zamiast `Option` w definicji modelu.
A>
A> Dzieci, prosimy, nie zaburzajcie spójności typeklas.


## Rzeczy `Object`owe

W rozdziale o Danych i Funkcjonalnościach powiedzieliśmy, że sposób w jaki JVM rozumie równość nie działa dla wielu rzeczy,
które możemy umieścić wewnątrz ADT. Problem ten wynika z faktu, że JVM był projektowany dla języka Java, a `equals` 
zdefiniowane jest w klasie `java.lang.Object`. Nie ma więc możliwości aby `equals` usunąć ani zagwarantować, że jest
zaimplementowany. 

Niemniej, w FP wolimy używać typeklas do wyrażania polimorficznych zachowań i koncept równości również może zostać
w ten sposób wyrażony.

{width=20%}
![](images/scalaz-comparable.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Equal[F]  {
    @op("===") def equal(a1: F, a2: F): Boolean
    @op("/==") def notEqual(a1: F, a2: F): Boolean = !equal(a1, a2)
  }
~~~~~~~~

`===` (*potrójne równa się*, _triple equals_) jest bezpieczniejszy względem typów (_typesafe_) niż `==` (*podwójne równa się*,
_double equals_), ponieważ użycie go wymaga, aby po obu stronach porównania znajdowały się instancje dokładnie tego samego typu.
W ten sposób możemy zapobiec wielu częstym błędom.

`equal` ma te same wymagania jak `Object.equals`

-   *przemienność* (_commutative_) `f1 === f2` implikuje `f2 === f1`
-   *zwrotność* (_reflexive_) `f === f`
-   *przechodniość* (_transitive_) `f1 === f2 && f2 === f3` implikuje `f1 === f3`

Poprzez odrzucenie konceptu uniwersalnego `Object.equals`, gdy konstruujemy ADT nie bierzemy za pewnik, że wiemy jak
porównywać instancje danego typu. Jeśli instancja `Equal` nie będzie dostępna, nasz kod się nie skompiluje. 

Kontynuując praktykę odrzucania zaszłości z Javy, zamiast mówić że dane są instancją `java.lang.Comparable`,
powiemy że mają instancję typeklasy `Order`:

{lang="text"}
~~~~~~~~
  @typeclass trait Order[F] extends Equal[F] {
    @op("?|?") def order(x: F, y: F): Ordering
  
    override  def equal(x: F, y: F): Boolean = order(x, y) == Ordering.EQ
    @op("<" ) def lt(x: F, y: F): Boolean = ...
    @op("<=") def lte(x: F, y: F): Boolean = ...
    @op(">" ) def gt(x: F, y: F): Boolean = ...
    @op(">=") def gte(x: F, y: F): Boolean = ...
  
    def max(x: F, y: F): F = ...
    def min(x: F, y: F): F = ...
    def sort(x: F, y: F): (F, F) = ...
  }
  
  sealed abstract class Ordering
  object Ordering {
    case object LT extends Ordering
    case object EQ extends Ordering
    case object GT extends Ordering
  }
~~~~~~~~

`Order` implementuje `.equal` wykorzystując nową metodę prostą `.order`. Kiedy typeklasa implementuje 
*kombinator prymitywny* rodzica za pomocą *kombinatora pochodnego*, musimy dodać *domniemane prawo podstawiania* 
(_implied law of substitution_). Jeśli instancja `Order` ma nadpisać `.equal` z powodów wydajnościowych, musi ona zachowywać
się dokładnie tak samo jak oryginał.

Rzeczy, które definiują porządek mogą również być dyskretne, pozwalając nam na przechodzenie do poprzedników i 
następników:

{lang="text"}
~~~~~~~~
  @typeclass trait Enum[F] extends Order[F] {
    def succ(a: F): F
    def pred(a: F): F
    def min: Option[F]
    def max: Option[F]
  
    @op("-+-") def succn(n: Int, a: F): F = ...
    @op("---") def predn(n: Int, a: F): F = ...
  
    @op("|->" ) def fromToL(from: F, to: F): List[F] = ...
    @op("|-->") def fromStepToL(from: F, step: Int, to: F): List[F] = ...
    @op("|=>" ) def fromTo(from: F, to: F): EphemeralStream[F] = ...
    @op("|==>") def fromStepTo(from: F, step: Int, to: F): EphemeralStream[F] = ...
  }
~~~~~~~~

{lang="text"}
~~~~~~~~
  scala> 10 |--> (2, 20)
  res: List[Int] = List(10, 12, 14, 16, 18, 20)
  
  scala> 'm' |-> 'u'
  res: List[Char] = List(m, n, o, p, q, r, s, t, u)
~~~~~~~~

A> `|-->` to Miecz Świetlny Scalaz. To jest właśnie składnia Programisty Funkcyjnego. Nie jakieś niezdarne albo losowe `fromStepToL`.
A>  Elegancka składnia... na bardziej cywilizowane czasy.

`EphemeralStream` omówimy w następnym rozdziale, na razie wystarczy nam wiedzieć że jest to potencjalnie nieskończona
struktura danych, która unika problemów z przetrzymywaniem pamięci obecnych w klasie `Stream` z biblioteki standardowej.

Podobnie do `Object.equals`, koncepcja metody `.toString` będącej dostępną w każdej klasie ma sens jedynie w Javie.
Chcielibyśmy wymusić możliwość konwersji do ciągu znaków w czasie kompilacji, i dokładnie to robi typeklasa `Show`:

{lang="text"}
~~~~~~~~
  trait Show[F] {
    def show(f: F): Cord = ...
    def shows(f: F): String = ...
  }
~~~~~~~~

Lepiej poznamy klasę `Cord` w rozdziale poświęconym typom danych, teraz jedyne co musimy wiedzieć, to że
`Cord` jest wydajną struktura danych służącą do przechowywania i manipulowania instancjami typu `String`.


## Rzeczy Mapowalne

Skupmy się teraz na rzeczach, które możemy w jakiś sposób przemapowywać (_map over_) lub trawersować (_traverse_):

{width=100%}
![](images/scalaz-mappable.png)


### Funktor

{lang="text"}
~~~~~~~~
  @typeclass trait Functor[F[_]] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
  
    def void[A](fa: F[A]): F[Unit] = map(fa)(_ => ())
    def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)] = map(fa)(a => (a, f(a)))
  
    def fpair[A](fa: F[A]): F[(A, A)] = map(fa)(a => (a, a))
    def strengthL[A, B](a: A, f: F[B]): F[(A, B)] = map(f)(b => (a, b))
    def strengthR[A, B](f: F[A], b: B): F[(A, B)] = map(f)(a => (a, b))
  
    def lift[A, B](f: A => B): F[A] => F[B] = map(_)(f)
    def mapply[A, B](a: A)(f: F[A => B]): F[B] = map(f)((ff: A => B) => ff(a))
  }
~~~~~~~~

Jedyną metodą abstrakcyjną jest `map`  i musi się ona *komponować* (_składać_, _compose_), tzn. mapowanie za pomocą `f` a następnie `g`
musi dawać ten sam wyniki jak mapowanie z użyciem złożenia tych funkcji (`f ∘ g`).

{lang="text"}
~~~~~~~~
  fa.map(f).map(g) == fa.map(f.andThen(g))
~~~~~~~~

Metoda `map` nie może też wykonywać żadnych zmian jeśli przekazana do niej funkcja to `identity` (czyli `x => x`)

{lang="text"}
~~~~~~~~
  fa.map(identity) == fa
  
  fa.map(x => x) == fa
~~~~~~~~

`Functor` definiuje kilka pomocniczych metod wokół `map`, które mogą być zoptymalizowane przez konkretne instancje.
Dokumentacja została celowo pominięta aby zachęcić do samodzielnego odgadnięcia co te metody robią zanim spojrzymy na ich
implementację. Poświęć chwilę na przestudiowanie samych sygnatur zanim ruszysz dalej:

{lang="text"}
~~~~~~~~
  def void[A](fa: F[A]): F[Unit]
  def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)]
  
  def fpair[A](fa: F[A]): F[(A, A)]
  def strengthL[A, B](a: A, f: F[B]): F[(A, B)]
  def strengthR[A, B](f: F[A], b: B): F[(A, B)]
  
  // harder
  def lift[A, B](f: A => B): F[A] => F[B]
  def mapply[A, B](a: A)(f: F[A => B]): F[B]
~~~~~~~~

1.  `void` przyjmuje instancję `F[A]` i zawsze zwraca
    `F[Unit]`, a więc gubi wszelkie przechowywane wartości ale zachowuje strukturę.
2.  `fproduct` przyjmuje takie same argumenty jak `map` ale zwraca `F[(A, B)]`,
    a więc łączy wynik operacji z wcześniejszą zawartością. Operacja ta przydaje się gdy chcemy zachować 
    argumenty przekazane do funkcji. 
3.  `fpair` powiela element `A` do postaci `F[(A, A)]`
4.  `strengthL` łączy zawartość `F[B]` ze stałą typu `A` po lewej stronie.
5.  `strengthR` łączy zawartość `F[A]` ze stałą typu `B` po prawej stronie.
6.  `lift` przyjmuje funkcję `A => B`i zwraca `F[A] => F[B]`. Innymi słowy przyjmuje funkcję która operuje na
    zawartości `F[A]` i zwraca funkcję która operuje na `F[A]` bezpośrednio.
7.  `mapply` to łamigłówka. Powiedzmy, że mamy `F[_]` z funkcją `A => B` w środku oraz wartość `A`, w rezultacie 
    możemy otrzymać `F[B]`. Sygnatura wygląda podobnie do `pure` ale wymaga od wołającego dostarczenia `F[A => B]`.

`fpair`, `strengthL` i `strengthR` wyglądają całkiem bezużytecznie, ale przydają się gdy chcemy zachować pewne informacje,
które w innym wypadku zostałyby utracone.

`Functor` ma też specjalną składnię:

{lang="text"}
~~~~~~~~
  implicit class FunctorOps[F[_]: Functor, A](self: F[A]) {
    def as[B](b: =>B): F[B] = Functor[F].map(self)(_ => b)
    def >|[B](b: =>B): F[B] = as(b)
  }
~~~~~~~~

`.as` i `>|` to metody pozwalające na zastąpienie wyniku przez przekazaną stałą.

A> Gdy Scalaz dostarcza funkcjonalności za pomocą rozszerzeń, zamiast bezpośrednio w typeklasie, dzieje się tak
A> z powodu kompatybilności binarnej.
A>
A> Gdy ukazuje się wersja `X.Y.0` Scalaz, nie ma możliwości dodania metod do typeklasy w tej samej serii wydań dla
A> Scali 2.10 i 2.11. Warto więc zawsze spojrzeć zarówno na definicję typeklasy jak i zdefiniowanej dla niej 
A> dodatkowej składni.

W naszej przykładowej aplikacji wprowadziliśmy jeden brzydki hack, definiując metody `start` i `stop` tak,
aby zwracały swoje własne wejście:

{lang="text"}
~~~~~~~~
  def start(node: MachineNode): F[MachineNode]
  def stop (node: MachineNode): F[MachineNode]
~~~~~~~~

Pozwala nam to opisywać logikę biznesową w bardzo zwięzły sposób, np.

{lang="text"}
~~~~~~~~
  for {
    _      <- m.start(node)
    update = world.copy(pending = Map(node -> world.time))
  } yield update
~~~~~~~~

albo

{lang="text"}
~~~~~~~~
  for {
    stopped <- nodes.traverse(m.stop)
    updates = stopped.map(_ -> world.time).toList.toMap
    update  = world.copy(pending = world.pending ++ updates)
  } yield update
~~~~~~~~

Ale hack ten wprowadza zbędną komplikację do implementacji. Lepiej będzie gdy pozwolimy naszej algebrze zwracać
`F[Unit]` a następnie użyjemy `as`:

{lang="text"}
~~~~~~~~
  m.start(node) as world.copy(pending = Map(node -> world.time))
~~~~~~~~

oraz

{lang="text"}
~~~~~~~~
  for {
    stopped <- nodes.traverse(a => m.stop(a) as a)
    updates = stopped.map(_ -> world.time).toList.toMap
    update  = world.copy(pending = world.pending ++ updates)
  } yield update
~~~~~~~~


### Foldable

Technicznie rzecz biorąc, `Foldable` przydaje się dla struktur danych, przez które możemy przejść
a na koniec wyprodukować wartość podsumowującą. Jednak stwierdzenie to nie oddaje pełnej natury tej
"jednotypeklasowej armii", która jest w stanie dostarczyć większość tego, co spodziewalibyśmy
się znaleźć w Collections API.

Do omówienia mamy tyle metod, że musimy je sobie podzielić. Zacznijmy od metod abstrakcyjnych:

{lang="text"}
~~~~~~~~
  @typeclass trait Foldable[F[_]] {
    def foldMap[A, B: Monoid](fa: F[A])(f: A => B): B
    def foldRight[A, B](fa: F[A], z: =>B)(f: (A, =>B) => B): B
    def foldLeft[A, B](fa: F[A], z: B)(f: (B, A) => B): B = ...
~~~~~~~~

Instancja `Foldable` musi zaimplementować jedynie `foldMap` i `foldRight` aby uzyskać pełną funkcjonalność
tej typeklasy, aczkolwiek poszczególne metody są często optymalizowane dla konkretnych struktur danych.

`.foldMap` ma alternatywną nazwę do celów marketingowych: **MapReduce**. Mając do dyspozycji `F[A]`, 
funkcję z `A` na `B` i sposób na łączenie `B` (dostarczony przez `Monoid` wraz z elementem zerowym),
możemy wyprodukować "podsumowującą" wartość typu `B`. Kolejność operacji nie jest narzucana, co pozwala
na wykonywanie ich równolegle.

`.foldRight` nie wymaga aby jej parametry miały instancję `Monoid`u, co oznacza, że musimy podać
wartość zerową `z` oraz sposób łączenia elementów z wartością podsumowująca. Kierunek przechodzenia jest
zdefiniowany jako od prawej do lewej, w związku z czym operacje nie mogą być zrównoleglone.

A> `foldRight` jest koncepcyjnie taka sama, jak `foldRight` z biblioteki standardowej scali. Jednak z tą drugą jest jeden
A> problem, który został rozwiązany w scalaz: bardzo duże struktury danych mogą powodować przepełnienie stosu (_stack overflow_).
A> `List.foldRight` oszukuje implementując `foldRight` jako odwrócony `foldLeft`.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   override def foldRight[B](z: B)(op: (A, B) => B): B =
A>     reverse.foldLeft(z)((right, left) => op(left, right))
A> ~~~~~~~~
A> 
A> ale koncepcja odwracania nie jest uniwersalna i to obejście nie może być stosowane dla wszystkich struktur danych.
A> Powiedzmy, że chcielibyśmy odnaleźć małą liczbę w kolekcji `Stream` z wczesnym wyjściem (_early exit_):
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> def isSmall(i: Int): Boolean = i < 10
A>   scala> (1 until 100000).toStream.foldRight(false) {
A>            (el, acc) => isSmall(el) || acc
A>          }
A>   java.lang.StackOverflowError
A>     at scala.collection.Iterator.toStream(Iterator.scala:1403)
A>     ...
A> ~~~~~~~~
A> 
A> Scalaz rozwiązuje ten problemy przyjmując wartość zagregowaną *poprzez nazwę* (_by name_)
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> (1 |=> 100000).foldRight(false)(el => acc => isSmall(el) || acc )
A>   res: Boolean = true
A> ~~~~~~~~
A> 
A> co oznacza, że wartość `acc` nie jest ewaluowana zanim nie będzie potrzebna
A> 
A> Warto pamiętać, że nie wszystkie operację użyte z `foldRight` są bezpieczne od przepełnienia stosu. Gdybyśmy
A> wymagali obliczenia wszystkich dostępnych elementów, również moglibyśmy otrzymać `StackOverflowError` używając
A> `EphemeralStream` ze Scalaz. 
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> (1L |=> 100000L).foldRight(0L)(el => acc => el |+| acc )
A>   java.lang.StackOverflowError
A>     at scalaz.Foldable.$anonfun$foldr$1(Foldable.scala:100)
A>     ...
A> ~~~~~~~~

`foldLeft` trawersuje elementy od lewej do prawej. Metoda ta może być zaimplementowana za pomocą `foldMap`, ale
większość instancji woli dostarczyć osobną implementację dla tak podstawowej operacji. Ponieważ z reguły implementacje 
tej metody są ogonowo rekursywne (_tail recursive_) nie ma tutaj parametrów przekazywanych *przez nazwę*.

Jedyny prawem obowiązującym `Foldable` jest to, że `foldLeft` i `foldRight` powinny być spójne z `foldMap` dla
operacji monoidalnych, np. dodawanie na koniec listy dla `foldLeft` i dodawanie na początek dla `foldRight`.
Niemniej `foldLeft` i `foldRight` nie muszą być zgodne ze sobą na wzajem: w rzeczywistości często produkują
odwrotne rezultaty.

Najprostszą rzeczą, którą możemy zrobić z `foldMap` to użyć funkcji `identity` i uzyskać tym samym `fold` 
(naturalną sumę elementów monoidalnych), z dodatkowymi wariantami pozwalającymi dobrać odpowiednią metodę 
zależnie od kryteriów wydajnościowych:

{lang="text"}
~~~~~~~~
  def fold[A: Monoid](t: F[A]): A = ...
  def sumr[A: Monoid](fa: F[A]): A = ...
  def suml[A: Monoid](fa: F[A]): A = ...
~~~~~~~~
 
Gdy uczyliśmy się o `Monoid`zie, napisaliśmy:

{lang="text"}
~~~~~~
  scala> templates.foldLeft(Monoid[TradeTemplate].zero)(_ |+| _)
~~~~~~~~

Teraz wiemy już, że było to niemądre i powinniśmy zrobić tak:

{lang="text"}
~~~~~~~~
  scala> templates.toIList.fold
  res: TradeTemplate = TradeTemplate(
                         List(2017-08-05,2017-09-05),
                         Some(USD),
                         Some(false))
~~~~~~~~

`.fold` nie zadziała na klasie `List` z biblioteki standardowej, ponieważ ta definiuje już metodę o nazwie
`fold`, która robi coś podobnego na swój własny sposób.

Osobliwie nazywająca się metoda `intercalate` wstawia konkretną instancję typu `A` pomiędzy każde dwa elementy przed wykonaniem `fold.`

{lang="text"}
~~~~~~~~
  def intercalate[A: Monoid](fa: F[A], a: A): A = ...
~~~~~~~~

i jest tym samym uogólnioną wersją `mkString`:

{lang="text"}
~~~~~~~~
  scala> List("foo", "bar").intercalate(",")
  res: String = "foo,bar"
~~~~~~~~

`foldLeft` pozwala na dostęp do konkretnego elementu poprzez jego indeks oraz daje nam kilka innych, blisko związanych 
metod:

{lang="text"}
~~~~~~~~
  def index[A](fa: F[A], i: Int): Option[A] = ...
  def indexOr[A](fa: F[A], default: =>A, i: Int): A = ...
  def length[A](fa: F[A]): Int = ...
  def count[A](fa: F[A]): Int = length(fa)
  def empty[A](fa: F[A]): Boolean = ...
  def element[A: Equal](fa: F[A], a: A): Boolean = ...
~~~~~~~~

Scalaz jest biblioteką czystą, składającą się wyłącznie z *funkcji totalnych*. Tam gdzie `List.apply` wyrzuca wyjątek,
`Foldable.index` zwraca `Option[A]` oraz pozwala użyć wygodnego `indexOr`, który zwraca `A` bazując na wartości domyślnej.
`.element` podobny jest do `.contains` z biblioteki standardowej ale używa `Equal` zamiast niejasno zdefiniowanego
pojęcia równości pochodzącego z JVMa.

Metody te *na prawdę* brzmią jak API kolekcji. No i oczywiście każdy obiekt mający instancje `Foldable` może być 
przekonwertowany na listę:

{lang="text"}
~~~~~~~~
  def toList[A](fa: F[A]): List[A] = ...
~~~~~~~~

Istnieją również konwersje do innych typów danych zarówno z biblioteki standardowej jak i Scalaz, takie jak:
`.toSet`, `.toVector`, `.toStream`, `.to[T <: TraversableLike]`, `.toIList` itd.

Dostępne są również przydatne metody do weryfikacji predykatów

{lang="text"}
~~~~~~~~
  def filterLength[A](fa: F[A])(f: A => Boolean): Int = ...
  def all[A](fa: F[A])(p: A => Boolean): Boolean = ...
  def any[A](fa: F[A])(p: A => Boolean): Boolean = ...
~~~~~~~~

`filterLength` zlicza elementy, które spełniają predykat, `all` i `any` zwracają `true` jeśli wszystkie (lub jakikolwiek)
elementy spełniają predykat i mogą zakończyć działanie bez sprawdzania wszystkich elementów.

A> W poprzednim rozdziale widzieliśmy `NonEmptyList`. Dla zwięzłości używać będziemy skrótu `Nel` zamiast `NonEmptyList`.
A>
A> Widzieliśmy też typ `IList `, który, jak pamiętacie, jest odpowiednikiem `List` ale pozbawionym
A> nieczystych metod, takich jak na przykład `apply`.

Możemy też podzielić `F[A]` na części bazując na kluczu `B` za pomocą metody `splitBy`

{lang="text"}
~~~~~~~~
  def splitBy[A, B: Equal](fa: F[A])(f: A => B): IList[(B, Nel[A])] = ...
  def splitByRelation[A](fa: F[A])(r: (A, A) => Boolean): IList[Nel[A]] = ...
  def splitWith[A](fa: F[A])(p: A => Boolean): List[Nel[A]] = ...
  def selectSplit[A](fa: F[A])(p: A => Boolean): List[Nel[A]] = ...
  
  def findLeft[A](fa: F[A])(f: A => Boolean): Option[A] = ...
  def findRight[A](fa: F[A])(f: A => Boolean): Option[A] = ...
~~~~~~~~

na przykład

{lang="text"}
~~~~~~~~
  scala> IList("foo", "bar", "bar", "faz", "gaz", "baz").splitBy(_.charAt(0))
  res = [(f, [foo]), (b, [bar, bar]), (f, [faz]), (g, [gaz]), (b, [baz])]
~~~~~~~~

Zwróć uwagę że otrzymaliśmy dwa elementy zaindeksowane za pomocą `'b'`.

`splitByRelation` pozwala uniknąć dostarczania instancji `Equal` ale za to wymaga podania operatora porównującego.

`splitWith` dzieli elementy na grupy które spełniają i nie spełniają predykatu. `selectSplit` wybiera grupy elementów,
które spełniają predykat a pozostałe odrzuca. To jedna z tych rzadkich sytuacji gdzie dwie metody mają tę samą sygnaturę
ale działają inaczej. 

`findLeft` i `findRight` pozwalając znaleźć pierwszy element (od lewej lub prawej), który spełnia predykat.

Dalej korzystając z `Equal` i `Order` dostajemy metody `distinct`, które zwracają elementy unikalne.

{lang="text"}
~~~~~~~~
  def distinct[A: Order](fa: F[A]): IList[A] = ...
  def distinctE[A: Equal](fa: F[A]): IList[A] = ...
  def distinctBy[A, B: Equal](fa: F[A])(f: A => B): IList[A] =
~~~~~~~~

`distinct` jest zaimplementowany w sposób bardziej optymalny niż `distinctE`, ponieważ może używać kolejności
a dzięki niej odszukiwać elementy unikalne w sposób podobny do tego w jaki działa algorytm quicksort. Dzięki temu 
jest zdecydowanie szybszy niż `List.distinct`. Struktury danych (takie jak zbiory) mogą implementować `distinct` w 
swoich `Foldable` bez dodatkowego wysiłku. 

`distinctBy` pozwala na grupowanie bazując na rezultacie wywołania podanej funkcji na każdym z oryginalnych elementów.
Przykładowe użycie: grupowanie imion ze względu na pierwszą literę słowa.

Możemy wykorzystać `Order` również do odszukiwania elementów minimalnych i maksymalnych (lub obu ekstremów), wliczając w to
warianty używające `Of` lub `By` aby najpierw przemapować elementy do innego typu lub użyć innego typu do samego porównania.

{lang="text"}
~~~~~~~~
  def maximum[A: Order](fa: F[A]): Option[A] = ...
  def maximumOf[A, B: Order](fa: F[A])(f: A => B): Option[B] = ...
  def maximumBy[A, B: Order](fa: F[A])(f: A => B): Option[A] = ...
  
  def minimum[A: Order](fa: F[A]): Option[A] = ...
  def minimumOf[A, B: Order](fa: F[A])(f: A => B): Option[B] = ...
  def minimumBy[A, B: Order](fa: F[A])(f: A => B): Option[A] = ...
  
  def extrema[A: Order](fa: F[A]): Option[(A, A)] = ...
  def extremaOf[A, B: Order](fa: F[A])(f: A => B): Option[(B, B)] = ...
  def extremaBy[A, B: Order](fa: F[A])(f: A => B): Option[(A, A)] =
~~~~~~~~

Możemy na przykład zapytać o to, który element typu `String` jest maksimum ze względu (`By`) na swoją długość lub jaka jest maksymalna
długość elementów (`Of`).

{lang="text"}
~~~~~~~~
  scala> List("foo", "fazz").maximumBy(_.length)
  res: Option[String] = Some(fazz)
  
  scala> List("foo", "fazz").maximumOf(_.length)
  res: Option[Int] = Some(4)
~~~~~~~~

Podsumowuje to kluczowe funkcjonalności `Foldable`. Cokolwiek spodziewalibyśmy się zobaczyć
w API kolekcji, jest już prawdopodobnie dostępna dzięki `Foldable`, a jeśli nie jest to prawdopodobnie być powinno.

Na koniec spojrzymy na kilka wariacji metod, które widzieliśmy już wcześniej. Zacznijmy od tych, które przyjmują
instancję typu `Semigroup` zamiast `Monoid`:


{lang="text"}
~~~~~~~~
  def fold1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  def foldMap1Opt[A, B: Semigroup](fa: F[A])(f: A => B): Option[B] = ...
  def sumr1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  def suml1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  ...
~~~~~~~~

zwracając tym samym `Option` aby móc obsłużyć puste struktury danych (`Semigroup` nie definiuje elementu zerowego).

A> Metody te czytamy jako "one-Option", nie `10 pt`.

Typeklasa `Foldable1` zawiera dużo więcej wariantów bazujących na `Semigroup`ie (wszystkie z sufiksem `1`)
i używanie jej ma sens dla struktur które nigdy nie są puste, nie wymagając definiowania pełnego `Monoid`u dla elementów. 

Co ważne, istnieją również warianty pracujące w oparciu o typy monadyczne. Używaliśmy już `foldLeftM` kiedy po raz 
pierwszy pisaliśmy logikę biznesową naszej aplikacji. Teraz wiemy że pochodzi ona z `Foldable`:

{lang="text"}
~~~~~~~~
  def foldLeftM[G[_]: Monad, A, B](fa: F[A], z: B)(f: (B, A) => G[B]): G[B] = ...
  def foldRightM[G[_]: Monad, A, B](fa: F[A], z: =>B)(f: (A, =>B) => G[B]): G[B] = ...
  def foldMapM[G[_]: Monad, A, B: Monoid](fa: F[A])(f: A => G[B]): G[B] = ...
  def findMapM[M[_]: Monad, A, B](fa: F[A])(f: A => M[Option[B]]): M[Option[B]] = ...
  def allM[G[_]: Monad, A](fa: F[A])(p: A => G[Boolean]): G[Boolean] = ...
  def anyM[G[_]: Monad, A](fa: F[A])(p: A => G[Boolean]): G[Boolean] = ...
  ...
~~~~~~~~


### Traverse

`Traverse` to skrzyżowanie `Functor`a z `Foldable`

{lang="text"}
~~~~~~~~
  trait Traverse[F[_]] extends Functor[F] with Foldable[F] {
    def traverse[G[_]: Applicative, A, B](fa: F[A])(f: A => G[B]): G[F[B]]
    def sequence[G[_]: Applicative, A](fga: F[G[A]]): G[F[A]] = ...
  
    def reverse[A](fa: F[A]): F[A] = ...
  
    def zipL[A, B](fa: F[A], fb: F[B]): F[(A, Option[B])] = ...
    def zipR[A, B](fa: F[A], fb: F[B]): F[(Option[A], B)] = ...
    def indexed[A](fa: F[A]): F[(Int, A)] = ...
    def zipWithL[A, B, C](fa: F[A], fb: F[B])(f: (A, Option[B]) => C): F[C] = ...
    def zipWithR[A, B, C](fa: F[A], fb: F[B])(f: (Option[A], B) => C): F[C] = ...
  
    def mapAccumL[S, A, B](fa: F[A], z: S)(f: (S, A) => (S, B)): (S, F[B]) = ...
    def mapAccumR[S, A, B](fa: F[A], z: S)(f: (S, A) => (S, B)): (S, F[B]) = ...
  }
~~~~~~~~

Na początku rozdziału pokazaliśmy jak ważne są `traverse` i `sequence` gdy chcemy odwrócić kolejność konstruktorów typów
(np. z `List[Future[_]]` na `Future[List[_]]`).

W `Foldable` nie mogliśmy założyć że `reverse` jest konceptem uniwersalnym, ale teraz już możemy.

Możemy też `zip`ować ze sobą dwie rzeczy które mają instancję `Traverse`, dostając `None` gdy jedna ze stron
nie ma już więcej elementów. Specjalny wariantem tej operacji jest dodanie indeksów do każdego elementu za pomocą
`indexed`.

`zipWithL` i `zipWithR` pozwalają połączyć elementy w nowy typ i od razu stworzyć `F[C]`.

`mapAccumL` i `mapAccumR` to standardowe `map` połączone z akumulatorem. Jeśli nawyki z Javy każą nam sięgnąć po `var`
i używaj jej wewnątrz `map` to najprawdopodobniej powinniśmy używać `mapAccumL`.

Powiedzmy, że mamy listę słów i chcielibyśmy ukryć te, które już wcześniej widzieliśmy. Chcemy aby algorytm działał również
dla nieskończonych strumieni danych, a więc może on przetworzyć kolekcję jedynie raz.

{lang="text"}
~~~~~~~~
  scala> val freedom =
  """We campaign for these freedoms because everyone deserves them.
     With these freedoms, the users (both individually and collectively)
     control the program and what it does for them."""
     .split("\\s+")
     .toList
  
  scala> def clean(s: String): String = s.toLowerCase.replaceAll("[,.()]+", "")
  
  scala> freedom
         .mapAccumL(Set.empty[String]) { (seen, word) =>
           val cleaned = clean(word)
           (seen + cleaned, if (seen(cleaned)) "_" else word)
         }
         ._2
         .intercalate(" ")
  
  res: String =
  """We campaign for these freedoms because everyone deserves them.
     With _ _ the users (both individually and collectively)
     control _ program _ what it does _ _"""
~~~~~~~~

Na koniec `Traverse1`, podobnie jak `Foldable1`, dostarcza warianty wspomnianych metod dla struktur danych, które nigdy nie są
puste, przyjmując słabszą `Semigroup`ę zamiast `Monoid`u i `Apply` zamiast `Applicative`. Przypomnijmy, że 
`Semigroup` nie musi dostarczać `.empty` a `Apply` nie wymaga `.point`.


### Align

`Align` służy do łączenia i wyrównywania wszystkiego co ma instancję typu `Functor`. Zanim spojrzymy
na `Align`, poznajmy typ danych `\&/` (wymawiany jako *Te*, *These* lub *hurray!*),

{lang="text"}
~~~~~~~~
  sealed abstract class \&/[+A, +B]
  final case class This[A](aa: A) extends (A \&/ Nothing)
  final case class That[B](bb: B) extends (Nothing \&/ B)
  final case class Both[A, B](aa: A, bb: B) extends (A \&/ B)
~~~~~~~~

A więc jest to wyrażenie alternatywy łącznej `OR`: `A` lub `B` lub oba `A` i `B`.

{lang="text"}
~~~~~~~~
  @typeclass trait Align[F[_]] extends Functor[F] {
    def alignWith[A, B, C](f: A \&/ B => C): (F[A], F[B]) => F[C]
    def align[A, B](a: F[A], b: F[B]): F[A \&/ B] = ...
  
    def merge[A: Semigroup](a1: F[A], a2: F[A]): F[A] = ...
  
    def pad[A, B]: (F[A], F[B]) => F[(Option[A], Option[B])] = ...
    def padWith[A, B, C](f: (Option[A], Option[B]) => C): (F[A], F[B]) => F[C] = ...
~~~~~~~~

`alignWith` przyjmuje funkcję z albo `A` albo `B` (albo obu) na `C` i zwraca wyniesioną funkcję z tupli `F[A]` i `F[B]`
na `F[C]`. `align` konstruuje `\&/` z dwóch `F[_]`.

`merge` pozwala nam połączyć dwie instancje `F[A]` tak długo jak jesteśmy w stanie dostarczyć instancję `Semigroup[A]`.
Dla przykładu, `Semigroup[Map[K,V]]] ` deleguje logikę to `Semigroup[V]`, łącząc wartości dla tych samych kluczy, a w
konsekwencji sprawiając, że `Map[K, List[A]]` zachowuje się jak multimapa:

{lang="text"}
~~~~~~~~
  scala> Map("foo" -> List(1)) merge Map("foo" -> List(1), "bar" -> List(2))
  res = Map(foo -> List(1, 1), bar -> List(2))
~~~~~~~~

a `Map[K, Int]` po prostu sumuje wartości.

{lang="text"}
~~~~~~~~
  scala> Map("foo" -> 1) merge Map("foo" -> 1, "bar" -> 2)
  res = Map(foo -> 2, bar -> 2)
~~~~~~~~

`.pad` i `.padWith` służą do częściowego łącznie struktur danych, które mogą nie mieć wymaganych wartości po jednej ze stron.
Dla przykładu, jeśli chcielibyśmy zagregować niezależne głosy i zachować informację skąd one pochodziły:

{lang="text"}
~~~~~~~~
  scala> Map("foo" -> 1) pad Map("foo" -> 1, "bar" -> 2)
  res = Map(foo -> (Some(1),Some(1)), bar -> (None,Some(2)))
  
  scala> Map("foo" -> 1, "bar" -> 2) pad Map("foo" -> 1)
  res = Map(foo -> (Some(1),Some(1)), bar -> (Some(2),None))
~~~~~~~~

Istnieją też wygodne warianty `align`, które używają struktury `\&/`

{lang="text"}
~~~~~~~~
  ...
    def alignSwap[A, B](a: F[A], b: F[B]): F[B \&/ A] = ...
    def alignA[A, B](a: F[A], b: F[B]): F[Option[A]] = ...
    def alignB[A, B](a: F[A], b: F[B]): F[Option[B]] = ...
    def alignThis[A, B](a: F[A], b: F[B]): F[Option[A]] = ...
    def alignThat[A, B](a: F[A], b: F[B]): F[Option[B]] = ...
    def alignBoth[A, B](a: F[A], b: F[B]): F[Option[(A, B)]] = ...
  }
~~~~~~~~

i które powinny być jasne po przeczytaniu sygnatur. Przykłady:

{lang="text"}
~~~~~~~~
  scala> List(1,2,3) alignSwap List(4,5)
  res = List(Both(4,1), Both(5,2), That(3))
  
  scala> List(1,2,3) alignA List(4,5)
  res = List(Some(1), Some(2), Some(3))
  
  scala> List(1,2,3) alignB List(4,5)
  res = List(Some(4), Some(5), None)
  
  scala> List(1,2,3) alignThis List(4,5)
  res = List(None, None, Some(3))
  
  scala> List(1,2,3) alignThat List(4,5)
  res = List(None, None, None)
  
  scala> List(1,2,3) alignBoth List(4,5)
  res = List(Some((1,4)), Some((2,5)), None)
~~~~~~~~

Zauważ, że warianty `A` i `B` używają alternatywy łącznej, a `This` i `That` są wykluczające,
zwracając `None` gdy wartość istnieje po obu stronach lub nie istnieje po wskazanej stronie.


## Wariancja

Musimy wrócić na moment do `Functor`a i omówić jego przodka, którego wcześniej zignorowaliśmy

{width=100%}
![](images/scalaz-variance.png)

`InvariantFunctor`, znany również jako *funktor wykładniczy*, definiuje metodę `xmap`, która pozwala zamienić
`F[A]` w `F[B]` jeśli przekażemy do niej funkcje z `A` na `B` i z `B` na `A`.

`Functor` to skrócona nazwa na to co powinno nazywać się *funktorem kowariantnym*.
Podobnie `Contravariant` to tak na prawdę *funktor kontrawariantny*.

`Functor` implementuje metodę `xmap` za pomocą `map` i ignoruje funkcję z `B` na `A`. `Contravariant` z kolei
implementuję ją z użyciem `contramap` i ignoruje funkcję z `A` na `B`:

{lang="text"}
~~~~~~~~
  @typeclass trait InvariantFunctor[F[_]] {
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B]
    ...
  }
  
  @typeclass trait Functor[F[_]] extends InvariantFunctor[F] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B] = map(fa)(f)
    ...
  }
  
  @typeclass trait Contravariant[F[_]] extends InvariantFunctor[F] {
    def contramap[A, B](fa: F[A])(f: B => A): F[B]
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B] = contramap(fa)(g)
    ...
  }
~~~~~~~~

Co istotne, określenia *kowariantny*, *kontrawariantny* i *inwariantny*, mimo że związane na poziomie
teoretycznym, nie przekładają się bezpośrednio na znaną ze Scali wariancję typów (czyli modyfikatory `+` i `-` 
umieszczane przy parametrach typów). *Inwariancja* oznacza tutaj, że możliwym jest przetłumaczenie zawartości
`F[A]` do `F[B]`. Używając `identity` możemy zobaczyć że `A` może być w bezpieczny sposób zrzutowane (w górę lub w dół)
do `B`, zależnie od wariancji funktora.

`.map` może być rozumiana poprzez swój kontrakt: "jeśli dasz mi `F` dla `A` i sposób na zamianę `A` w `B`, wtedy dam ci `F` dla `B`".

Podobnie, `.contramap` mówi że: "jeśli dasz mi `F` dla `A` i sposób na zamianę `B` w `A`, wtedy dam ci `F` dla `B`".

Rozważymy następujący przykład: w naszej aplikacji wprowadzamy typy domenowe `Alpha`, `Beta` i `Gamma` aby zabezpieczyć się
przed pomieszaniem liczb w kalkulacjach finansowych:

{lang="text"}
~~~~~~~~
  final case class Alpha(value: Double)
~~~~~~~~

ale sprawia to że nie mamy żadnych instancji typeklas dla tych nowych typów. Jeśli chcielibyśmy użyć takich
wartości w JSONie, musielibyśmy dostarczyć `JsEncoder` i `JsDecoder`.

Jednakże, `JsEncoder` ma instancję typeklasy `Contravariant` a `JsDecoder` typeklasy `Functor`, a więc możemy
wyderywować potrzebne nam instancje spełniając kontrakt:

-   "jeśli dasz mi `JsDecoder` dla `Double` i sposób na zamianę `Double` w `Alpha`, wtedy dam ci `JsDecoder` dla `Alpha`".
-   "jeśli dasz mi `JsEncoder` dla `Double` i sposób na zamianę `Alpha` w `Double`, wtedy dam ci `JsEncoder` dla `Alpha`".

{lang="text"}
~~~~~~~~
  object Alpha {
    implicit val decoder: JsDecoder[Alpha] = JsEncoder[Double].map(_.value)
    implicit val encoder: JsEncoder[Alpha] = JsEncoder[Double].contramap(_.value)
  }
~~~~~~~~

Metody w klasie mogą ustawić swoje parametry typu w *pozycji kontrawariantnej* (parametry metody) lub
w *pozycji kowariantnej* (typ zwracany). Jeśli typeklasa łączy pozycje kowariantne i kontrawariantne może oznaczać to, że
ma instancję typeklasy `InvariantFunctor` ale nie `Functor` ani `Contrawariant`.

## Apply i Bind

Potraktuj to jako rozgrzewkę przez typami `Applicative` i `Monad`

{width=100%}
![](images/scalaz-apply.png)


### Apply

`Apply` rozszerza typeklasę `Functor` poprzez dodanie metody `ap` która jest podobna do `map` w tym, że aplikuje funkcje na wartościach.
Jednak w przypadku `ap` funkcja jest opakowana w ten sam kontekst co wartości które są do niej przekazywane.

{lang="text"}
~~~~~~~~
  @typeclass trait Apply[F[_]] extends Functor[F] {
    @op("<*>") def ap[A, B](fa: =>F[A])(f: =>F[A => B]): F[B]
    ...
~~~~~~~~

A> `<*>` to Zaawansowany TIE Fighter, taki sam jak ten którym latał Darth Vader. Odpowiedni bo wygląda jak
A> rozgniewany rodzic. Albo smutny Pikachu.

Warto poświęcić chwilę na zastanowienie się co to znaczy, że prosta struktura danych, taka jak `Option[A]`, posiada
następującą implementację `.ap`

{lang="text"}
~~~~~~~~
  implicit def option[A]: Apply[Option[A]] = new Apply[Option[A]] {
    override def ap[A, B](fa: =>Option[A])(f: =>Option[A => B]) = f match {
      case Some(ff) => fa.map(ff)
      case None    => None
    }
    ...
  }
~~~~~~~~

Aby zaimplementować `.ap` musimy najpierw wydostać funkcję `ff: A => B` z `f: Option[A => B]`, a następnie
możemy przemapować `fa` z jej użyciem. Ekstrakcja funkcji z kontekstu to ważna funkcjonalność, którą przynosi `Apply`. 
Pozwala tym samym na łączenie wielu funkcji wewnątrz jednego kontekstu.

Wracając do `Apply`, znajdziemy tam rodzinę funkcji `applyX`, która pozwala nam łączyć równoległe obliczenia a następnie
mapować ich połączone wyniki:

{lang="text"}
~~~~~~~~
  @typeclass trait Apply[F[_]] extends Functor[F] {
    ...
    def apply2[A,B,C](fa: =>F[A], fb: =>F[B])(f: (A, B) => C): F[C] = ...
    def apply3[A,B,C,D](fa: =>F[A],fb: =>F[B],fc: =>F[C])(f: (A,B,C) =>D): F[D] = ...
    ...
    def apply12[...]
~~~~~~~~

Potraktuj `.apply2` jako obietnicę: "jeśli dasz mi `F` z `A` i `F` z `B` oraz sposób na połączenie `A` i `B` w `C`, wtedy
mogę dać ci `F` z `C`". Istnieje wiele zastosowań dla tej obietnicy, a 2 najważniejsze to:

-   tworzenie typeklas dla produktu `C` z jego składników `A` i `B`
-   wykonywanie *efektów* równolegle, jak w przypadku algebr dla drone i google, które stworzyliśmy w Rozdziale 3,
    a następnie łączenie ich wyników.

W rzeczy samej, `Apply` jest na tyle użyteczne że ma swoją własną składnię:

{lang="text"}
~~~~~~~~
  implicit class ApplyOps[F[_]: Apply, A](self: F[A]) {
    def *>[B](fb: F[B]): F[B] = Apply[F].apply2(self,fb)((_,b) => b)
    def <*[B](fb: F[B]): F[A] = Apply[F].apply2(self,fb)((a,_) => a)
    def |@|[B](fb: F[B]): ApplicativeBuilder[F, A, B] = ...
  }
  
  class ApplicativeBuilder[F[_]: Apply, A, B](a: F[A], b: F[B]) {
    def tupled: F[(A, B)] = Apply[F].apply2(a, b)(Tuple2(_))
    def |@|[C](cc: F[C]): ApplicativeBuilder3[C] = ...
  
    sealed abstract class ApplicativeBuilder3[C](c: F[C]) {
      ..ApplicativeBuilder4
        ...
          ..ApplicativeBuilder12
  }
~~~~~~~~

której użyliśmy w Rozdziale 3:

{lang="text"}
~~~~~~~~
  (d.getBacklog |@| d.getAgents |@| m.getManaged |@| m.getAlive |@| m.getTime)
~~~~~~~~

A> Operator `|@\` ma wiele imion. Niektórzy nazywają go *Składnią Produktu Kartezjańskiego* (_Cartesian Product Syntax_),
A> inni wolą *Cinnamon Bun*, *Admirał Ackbar* lub *Macaulay Culkin*. My wolimy nazywać go *Krzyk* (_The Scream_)
A> przez podobieństwo do obrazu Muncha oraz przez to że jest to dźwięk jaki wydaje procesor gdy równolegle oblicza
A> Wszystko.

Operatory `<*` i `*>` (prawy i lewy ptak) oferują wygodny sposób na zignorowanie wyniku jednego z dwóch równoległych 
efektów.

Niestety, mimo wygody którą daje operator `|@\`, jest z nim jeden problem: dla każdego kolejnego efektu alokowany jest
nowy obiekt typu `ApplicativeBuilder`. Gdy prędkość obliczeń ograniczona jest przez operacje I/O nie ma to znaczenia.
Jednak gdy wykonujesz obliczenia w całości na CPU lepiej jest użyć *krotnego wynoszenia* (_lifting with arity_), które nie
produkuje żadnych obiektów pośrednich:

{lang="text"}
~~~~~~~~
  def ^[F[_]: Apply,A,B,C](fa: =>F[A],fb: =>F[B])(f: (A,B) =>C): F[C] = ...
  def ^^[F[_]: Apply,A,B,C,D](fa: =>F[A],fb: =>F[B],fc: =>F[C])(f: (A,B,C) =>D): F[D] = ...
  ...
  def ^^^^^^[F[_]: Apply, ...]
~~~~~~~~

na przykład:

{lang="text"}
~~~~~~~~
  ^^^^(d.getBacklog, d.getAgents, m.getManaged, m.getAlive, m.getTime)
~~~~~~~~

Możemy też zawołać `applyX` bezpośrednio:

{lang="text"}
~~~~~~~~
  Apply[F].apply5(d.getBacklog, d.getAgents, m.getManaged, m.getAlive, m.getTime)
~~~~~~~~

Mimo tego, że `Apply` używany jest najczęściej z efektami, działa równie dobrze ze strukturami danych. Rozważ przepisanie

{lang="text"}
~~~~~~~~
  for {
    foo <- data.foo: Option[String]
    bar <- data.bar: Option[Int]
  } yield foo + bar.shows
~~~~~~~~

jako

{lang="text"}
~~~~~~~~
  (data.foo |@| data.bar)(_ + _.shows)
~~~~~~~~

Gdy chcemy jedynie połączyć wyniki w tuple, istnieją metody które robią dokładnie to

{lang="text"}
~~~~~~~~
  @op("tuple") def tuple2[A,B](fa: =>F[A],fb: =>F[B]): F[(A,B)] = ...
  def tuple3[A,B,C](fa: =>F[A],fb: =>F[B],fc: =>F[C]): F[(A,B,C)] = ...
  ...
  def tuple12[...]
~~~~~~~~

{lang="text"}
~~~~~~~~
  (data.foo tuple data.bar) : Option[(String, Int)]
~~~~~~~~

Są też uogólnione wersje `ap` dla więcej niż dwóch parametrów:

{lang="text"}
~~~~~~~~
  def ap2[A,B,C](fa: =>F[A],fb: =>F[B])(f: F[(A,B) => C]): F[C] = ...
  def ap3[A,B,C,D](fa: =>F[A],fb: =>F[B],fc: =>F[C])(f: F[(A,B,C) => D]): F[D] = ...
  ...
  def ap12[...]
~~~~~~~~

razem z wariantami `.lift`, które przyjmują zwykłe funkcje i wynoszą je do kontekstu `F[_]`, uogólniając `Functor.lift`

{lang="text"}
~~~~~~~~
  def lift2[A,B,C](f: (A,B) => C): (F[A],F[B]) => F[C] = ...
  def lift3[A,B,C,D](f: (A,B,C) => D): (F[A],F[B],F[C]) => F[D] = ...
  ...
  def lift12[...]
~~~~~~~~

oraz `.apF`, częściowo zaaplikowana wersja `ap`

{lang="text"}
~~~~~~~~
  def apF[A,B](f: =>F[A => B]): F[A] => F[B] = ...
~~~~~~~~

A na koniec `.forever`

{lang="text"}
~~~~~~~~
  def forever[A, B](fa: F[A]): F[B] = ...
~~~~~~~~

który powtarza efekt w nieskończoność bez zatrzymywania się. Przy jej użyciu instancja `Apply` musi być stack-safe, w przeciwnym wypadku
wywołanie spowoduje `StackOverflowError`. 


### Bind

`Bind` wprowadza metodę `.bind`, która jest synonimiczna do `.flatMap` i pozwala na mapowanie efektów/struktur danych
z użyciem funkcji zwracających nowy efekt/strukturę danych bez wprowadzania dodatkowych zagnieżdżeń.

{lang="text"}
~~~~~~~~
  @typeclass trait Bind[F[_]] extends Apply[F] {
  
    @op(">>=") def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = bind(fa)(f)
  
    override def ap[A, B](fa: =>F[A])(f: =>F[A => B]): F[B] =
      bind(f)(x => map(fa)(x))
    override def apply2[A, B, C](fa: =>F[A], fb: =>F[B])(f: (A, B) => C): F[C] =
      bind(fa)(a => map(fb)(b => f(a, b)))
  
    def join[A](ffa: F[F[A]]): F[A] = bind(ffa)(identity)
  
    def mproduct[A, B](fa: F[A])(f: A => F[B]): F[(A, B)] = ...
    def ifM[B](value: F[Boolean], t: =>F[B], f: =>F[B]): F[B] = ...
  
  }
~~~~~~~~

Metoda `.join` może wydawać się znajoma tym, którzy używali `.flatten` z biblioteki standardowej. Przyjmuje ona
zagnieżdżone konteksty i łączy je w jeden.

Wprowadzone zostały kombinatory pochodne dla `.ap` i `.apply2` aby zapewnić spójność z `.bind`. Zobaczymy później że 
to wymaganie niesie ze sobą konsekwencje dla strategii zrównoleglania.

`mproduct` przypomina `Functor.fproduct` i paruje wejście i wyjście funkcji wewnątrz `F`.

`ifM` to sposób na tworzenie warunkowych struktur danych lub efektów:

{lang="text"}
~~~~~~~~
  scala> List(true, false, true).ifM(List(0), List(1, 1))
  res: List[Int] = List(0, 1, 1, 0)
~~~~~~~~

`ifM` i `ap` są zoptymalizowane do cachowania i reużywania gałezi kodu. Porównajmy je z dłuższą wersją

{lang="text"}
~~~~~~~~
  scala> List(true, false, true).flatMap { b => if (b) List(0) else List(1, 1) }
~~~~~~~~

która produkuje nowe `List(0)` i `List(1, 1)` za każdym razem gdy dana gałąź jest wywoływana.

A> Tego rodzaju optymizacje są możliwe w FP, ponieważ wszystkie metody są deterministyczne, lub inaczej mówiąc
A> *referencyjnie transparentne*.
A>
A> Jeśli metoda zwraca inne wartości dla tych samych argumentów, jest ona *nieczysta* i zaburza rozumowanie oraz
A> optymalizacje, które moglibyśmy zastosować.
A>
A> Jeśli `F` jest efektem, być może jedną z naszych algebr, nie oznacza to że wynik wywołania algebry jest cachowany.
A> Raczej powinniśmy powiedzieć że cachowana jest referencja do operacji. Wydajnościowa optymalizacja `ifM` istotna jest
A> tylko dla struktur danych i staje się tym wyraźniejsza im bardziej skomplikowana praca dzieje się w danej gałęzi.
A> 
A> Dogłędbniej zbadamy koncepcje determinizmu i cachowania wartości w następnym rozdziale.

`Bind` wprowadza też specjalne operatory:

{lang="text"}
~~~~~~~~
  implicit class BindOps[F[_]: Bind, A] (self: F[A]) {
    def >>[B](b: =>F[B]): F[B] = Bind[F].bind(self)(_ => b)
    def >>![B](f: A => F[B]): F[A] = Bind[F].bind(self)(a => f(a).map(_ => a))
  }
~~~~~~~~

Używając `>>`odrzucamy wejście do `bind`, a używając `>>!` odrzucamy wyjście` 


## Aplikatywy i Monady

Z punkty widzenia oferowanych funkcjonalności, `Applicative` to `Apply` z dodaną metodą `pure`, a `Monad`
rozszerza `Applicative` dodając `Bind`.

{width=100%}
![](images/scalaz-applicative.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Applicative[F[_]] extends Apply[F] {
    def point[A](a: =>A): F[A]
    def pure[A](a: =>A): F[A] = point(a)
  }
  
  @typeclass trait Monad[F[_]] extends Applicative[F] with Bind[F]
~~~~~~~~

Pod wieloma względami `Applicative` i `Monad` są zwieńczeniem wszystkiego co do tej pory widzieliśmy w tym rozdziale.
`.pure` (lub `.point` - alias powszechnie używany przy strukturach danych) pozwala nam na tworzenie efektów lub 
struktur danych z pojedynczych wartości.

Instancje `Applicative` muszę spełniać prawa gwarantujące spójność metod:

-   **Tożsamość (Identity)**: `fa <*> pure(identity) == fa` (gdzie `fa` to `F[A]`) - zaaplikowanie `pure(identity)` nic nie zmienia
-   **Homomorfizm (Homomorphism)**: `pure(a) <*> pure(ab) === pure(ab(a))`, (gdzie `ab` to funkcja `A => B`) - zaaplikowanie funkcji
    osadzonej w kontekście `F` za pomocą `pure` na wartości potraktowanej w ten sam sposób jest równoznaczne z wywołaniem
    tej funkcji na wspomnianej wartości i wywołaniem `pure` na rezultacie.
-   **Zamiana (Interchange)**: `pure(a) <*> fab === fab <*> pure(f => f(a))`, (gdzie `fab` to `F[A => B]`) - `pure` jest tożsama lewo- i prawostronnie
-   **Mappy**: `map(fa)(f) === fa <*> pure(f)`

`Monad` dodaje następujące prawa

-   **Tożsamość lewostronna (Left Identity)**: `pure(a).bind(f) === f(a)`
-   **Tożsamość prawostronna (Right Identity)**: `a.bind(pure(_)) === a`
-   **Łączność (Associativity)**: `fa.bind(f).bind(g) === fa.bind(a => f(a).bind(g))` gdzie
    `fa` to `F[A]`, `f` to `A => F[B]`, a `g` to `B => F[C]`.
    
Łączność mówi nam że połączone wywołania `bind` muszą być zgodne z wywołaniami zagnieżdżonymi. Jednakże, 
nie oznacza to że możemy zamieniać kolejność wywołań - to gwarantowała by *przemienność* (_commutativity_).
Dla przykładu, pamiętając, że `flatMap` to alias na `bind`, nie możemy zamienić

{lang="text"}
~~~~~~~~
  for {
    _ <- machine.start(node1)
    _ <- machine.stop(node1)
  } yield true
~~~~~~~~

na

{lang="text"}
~~~~~~~~
  for {
    _ <- machine.stop(node1)
    _ <- machine.start(node1)
  } yield true
~~~~~~~~

`start` i `stop` są **nie**-*przemienne*, ponieważ uruchomienie a następnie zatrzymanie węzła jest czymś innym
niż zatrzymanie i uruchomienie.

Nie mniej, zarówno `start` jak i `stop` są przemienne same ze sobą, a więc możemy zamienić

{lang="text"}
~~~~~~~~
  for {
    _ <- machine.start(node1)
    _ <- machine.start(node2)
  } yield true
~~~~~~~~

na

{lang="text"}
~~~~~~~~
  for {
    _ <- machine.start(node2)
    _ <- machine.start(node1)
  } yield true
~~~~~~~~

Obie formy są równoznaczne w tym konkretnym przypadku ale nie w ogólności. Robimy tutaj dużo założeń
co do Google Container API, ale wydaje się to być rozsądnych wyjściem.

Okazuje się, że w konsekwencji powyższych praw `Monad`a musi być przemienna, jeśli chcemy pozwolić na równoległe
działanie metod `applyX`. W Rozdziale 3 oszukaliśmy uruchamiając efekty w ten sposób

{lang="text"}
~~~~~~~~
  (d.getBacklog |@| d.getAgents |@| m.getManaged |@| m.getAlive |@| m.getTime)
~~~~~~~~

ponieważ wiedzieliśmy, że są one ze sobą przemienne. Kiedy w dalszych rozdziałach zajmiemy się interpretacją 
naszej aplikacji, dostarczymy dowód na przemienność operacji lub pozwolimy na uruchomienie ich sekwencyjnie.

Subtelności sposobów radzenia sobie z porządkowanie efektów, i tym czym te efekty tak naprawdę są, zasługują
na osobny rozdział. Porozmawiamy o nich przy Zaawansowanych Monadach.


## Dziel i Rządź

{width=100%}
![](images/scalaz-divide.png)

`Divide` to kontrawariantny odpowiednik `Apply`

{lang="text"}
~~~~~~~~
  @typeclass trait Divide[F[_]] extends Contravariant[F] {
    def divide[A, B, C](fa: F[A], fb: F[B])(f: C => (A, B)): F[C] = divide2(fa, fb)(f)
  
    def divide1[A1, Z](a1: F[A1])(f: Z => A1): F[Z] = ...
    def divide2[A, B, C](fa: F[A], fb: F[B])(f: C => (A, B)): F[C] = ...
    ...
    def divide22[...] = ...
~~~~~~~~

`divide` mówi nam, że jeśli potrafimy podzielić `C` na `A` i `B` oraz mamy do dyspozycji `F[A]` i `F[B]`
to możemy stworzyć `F[C]`. Stąd też *dziel i rządź*.

Jest to świetny sposób na generowanie instancji kowariantnych typeklas dla typów będących produktami poprzez 
podzielenie tychże produktów na części. Scalaz oferuje instancje `Divide[Equal]`, spróbujmy więc stworzyć `Equal`
dla nowego typu `Foo`

{lang="text"}
~~~~~~~~
  scala> case class Foo(s: String, i: Int)
  scala> implicit val fooEqual: Equal[Foo] =
           Divide[Equal].divide2(Equal[String], Equal[Int]) {
             (foo: Foo) => (foo.s, foo.i)
           }
  scala> Foo("foo", 1) === Foo("bar", 1)
  res: Boolean = false
~~~~~~~~

Podążając za `Apply`, `Divide` również dostarcza zwięzłą składnię dla tupli

{lang="text"}
~~~~~~~~
  ...
    def tuple2[A1, A2](a1: F[A1], a2: F[A2]): F[(A1, A2)] = ...
    ...
    def tuple22[...] = ...
  }
~~~~~~~~

Ogólnie rzecz biorąc, jeśli typeklasa, oprócz instancji `Contravariant`, jest w stanie dostarczyć również `Divide`,
to znaczy, że jesteśmy w stanie wyderywować jej instancje dla dowolnej case klasy. Sprawa wygląda analogicznie dla
typeklas kowariantnych z instancją `Apply`. Zgłębimy ten temat w rozdziale poświęconym Derywacji Typeklas.

`Divisible` to odpowiednik `Applicative` dla rodziny `Contravariant`. Wprowadzana ona metodę `.conquer`, odpowiednik `.pure`:

{lang="text"}
~~~~~~~~
  @typeclass trait Divisible[F[_]] extends Divide[F] {
    def conquer[A]: F[A]
  }
~~~~~~~~

`.conquer` pozwala na tworzenie trywialnych implementacji, w których parametr typu jest ignorowany. Takie instancje 
nazywane są *ogólnie kwantyfikowanymi* (_universally quantified_). Na przykład, `Divisible[Equal].conquer[INil[String]]` tworzy
instancję `Equal`, która zawsze zwraca `true`.


## Plus

{width=100%}
![](images/scalaz-plus.png)

`Plus` to `Semigroup`a dla konstruktorów typu a `PlusEmpty` to odpowiednik `Monoid`u (obowiązują ich nawet te same prawa).
Nowością jest `IsEmpty`, które pozwala na sprawdzenie czy `F[A]` jest puste:

{lang="text"}
~~~~~~~~
  @typeclass trait Plus[F[_]] {
    @op("<+>") def plus[A](a: F[A], b: =>F[A]): F[A]
  }
  @typeclass trait PlusEmpty[F[_]] extends Plus[F] {
    def empty[A]: F[A]
  }
  @typeclass trait IsEmpty[F[_]] extends PlusEmpty[F] {
    def isEmpty[A](fa: F[A]): Boolean
  }
~~~~~~~~

A> `<+>` to TIE Interceptor, co sprawia że prawie wyczerpaliśmy gamę myśliwców TIE

Pozornie może się wydawać, że `<+>` zachowuje się tak samo jak `|+|`

{lang="text"}
~~~~~~~~
  scala> List(2,3) |+| List(7)
  res = List(2, 3, 7)
  
  scala> List(2,3) <+> List(7)
  res = List(2, 3, 7)
~~~~~~~~

Najlepiej jest przyjąć, że `<+>` operuje jedynie na `F[_]` nigdy nie patrząc na zawartość.
Przyjęła się konwencja, że `Plus` ignoruje porażki i wybiera "pierwszego zwycięzcę". Dzięki temu
`<+>` może być używany jako mechanizm szybkiego wyjścia oraz obsługi porażek przez fallbacki.

{lang="text"}
~~~~~~~~
  scala> Option(1) |+| Option(2)
  res = Some(3)
  
  scala> Option(1) <+> Option(2)
  res = Some(1)
  
  scala> Option.empty[Int] <+> Option(1)
  res = Some(1)
~~~~~~~~

Na przykład, jeśli chcielibyśmy pominąć obiekty `None` wewnątrz `NonEmptyList[Option[Int]]` i wybrać pierwszego
zwycięzcę (`Some`), możemy użyć `<+>` w połączeniu z `Foldable1.foldRight1`:

{lang="text"}
~~~~~~~~
  scala> NonEmptyList(None, None, Some(1), Some(2), None)
         .foldRight1(_ <+> _)
  res: Option[Int] = Some(1)
~~~~~~~~

Teraz, gdy znamy już `Plus`, okazuje się że wcale nie musieliśmy zaburzać koherencji typeklas w sekcji o Rzeczach Złączalnych
(definiując lokalną instancję `Monoid[Option[A]]`). Naszym celem było "wybranie ostatniego zwycięzcy",
co jest tożsame z wybranie pierwszego po odwróceniu kolejności elementów. Zwróć uwagę na użycie Interceptora TIE z
`ccy` i `otc` w odwróconej kolejności.

{lang="text"}
~~~~~~~~
  implicit val monoid: Monoid[TradeTemplate] = Monoid.instance(
    (a, b) => TradeTemplate(a.payments |+| b.payments,
                            b.ccy <+> a.ccy,
                            b.otc <+> a.otc),
    TradeTemplate(Nil, None, None)
  )
~~~~~~~~

`Applicative` i `Monad` mają wyspecjalizowaną wersję `PlusEmpty`

{lang="text"}
~~~~~~~~
  @typeclass trait ApplicativePlus[F[_]] extends Applicative[F] with PlusEmpty[F]
  
  @typeclass trait MonadPlus[F[_]] extends Monad[F] with ApplicativePlus[F] {
    def unite[T[_]: Foldable, A](ts: F[T[A]]): F[A] = ...
  
    def withFilter[A](fa: F[A])(f: A => Boolean): F[A] = ...
  }
~~~~~~~~

`.unite` pozwala nam zwinąć strukturę danych używając `PlusEmpty[F].monoid` zamiast `Monoidu` zdefiniowanego dla
typu wewnętrznego. Dla `List[Either[String, Int]]` oznacza to, że instancje `Left[String]` zamieniane są na `.empty`,
a następnie wszytko jest złączane. Jest to wygodny sposób na pozbycie się błędów:

{lang="text"}
~~~~~~~~
  scala> List(Right(1), Left("boo"), Right(2)).unite
  res: List[Int] = List(1, 2)
  
  scala> val boo: Either[String, Int] = Left("boo")
         boo.foldMap(a => a.pure[List])
  res: List[String] = List()
  
  scala> val n: Either[String, Int] = Right(1)
         n.foldMap(a => a.pure[List])
  res: List[Int] = List(1)
~~~~~~~~

`withFilter` pozwala nam na użycie konstrukcji `for`, którą opisywaliśmy z Rozdziale 2. Można nawet powiedzieć, że
Scala ma wbudowane wsparcie nie tylko dla `Monad` ale i `MonadPlus`!

Wracając na moment do `Foldable`, możemy odkryć kilka metod, których wcześniej nie omawialiśmy:

{lang="text"}
~~~~~~~~
  @typeclass trait Foldable[F[_]] {
    ...
    def msuml[G[_]: PlusEmpty, A](fa: F[G[A]]): G[A] = ...
    def collapse[X[_]: ApplicativePlus, A](x: F[A]): X[A] = ...
    ...
  }
~~~~~~~~

`msuml` wykonuje `fold` używając `Monoidu` z `PlusEmpty[G]`, a `collapse` używa `foldRight` w kombinacji
z instancją `PlusEmpty` typu docelowego:

{lang="text"}
~~~~~~~~
  scala> IList(Option(1), Option.empty[Int], Option(2)).fold
  res: Option[Int] = Some(3) // uses Monoid[Option[Int]]
  
  scala> IList(Option(1), Option.empty[Int], Option(2)).msuml
  res: Option[Int] = Some(1) // uses PlusEmpty[Option].monoid
  
  scala> IList(1, 2).collapse[Option]
  res: Option[Int] = Some(1)
~~~~~~~~


## Samotne Wilki

Niektóre z typeklas w Scalaz są w pełni samodzielne i nie należą do ogólnej hierarchii.

{width=80%}
![](images/scalaz-loners.png)


### Zippy

{lang="text"}
~~~~~~~~
  @typeclass trait Zip[F[_]]  {
    def zip[A, B](a: =>F[A], b: =>F[B]): F[(A, B)]
  
    def zipWith[A, B, C](fa: =>F[A], fb: =>F[B])(f: (A, B) => C)
                        (implicit F: Functor[F]): F[C] = ...
  
    def ap(implicit F: Functor[F]): Apply[F] = ...
  
    @op("<*|*>") def apzip[A, B](f: =>F[A] => F[B], a: =>F[A]): F[(A, B)] = ...
  
  }
~~~~~~~~

Metoda kluczowa tutaj to `zip`. Jest to słabsza wersja `Divide.tuple2`. Jeśli dostępny jest `Functor[F]` to 
`.zipWith` może zachowywać się jak `Apply.apply2`. Używając `ap` możemy nawet stworzyć pełnoprawne `Apply[F]` z
instancji `Zip[F]` i `Functor[F]`.

`.apzip` przyjmuje `F[A]` i wyniesioną funkcję `F[A] => F[B]` produkując `F[(A, B)]`, podobnie do `Functor.fproduct`.

A> `<*|*>` to operator przerażających Jawów z sagi Star Wars

{lang="text"}
~~~~~~~~
  @typeclass trait Unzip[F[_]]  {
    @op("unfzip") def unzip[A, B](a: F[(A, B)]): (F[A], F[B])
  
    def firsts[A, B](a: F[(A, B)]): F[A] = ...
    def seconds[A, B](a: F[(A, B)]): F[B] = ...
  
    def unzip3[A, B, C](x: F[(A, (B, C))]): (F[A], F[B], F[C]) = ...
    ...
    def unzip7[A ... H](x: F[(A, (B, ... H))]): ...
  }
~~~~~~~~

Bazą jest `unzip` dzielący `F[(A,B)]` na `F[A]` i `F[B]`, a `firsts` i `seconds` pozwalają na wybranie
jednej z części. Co ważne, `unzip` jest odwrotnością `zip`.

Metody od `unzip3` do `unzip7` to aplikacje `unzip`  pozwalające zmniejszyć ilość boilerplatu. Na przykład, 
jeśli dostaniemy garść zagnieżdżonych tupli to `Unzip[Id]` jest wygodnym sposobem na ich wypłaszczenie:

{lang="text"}
~~~~~~~~
  scala> Unzip[Id].unzip7((1, (2, (3, (4, (5, (6, 7)))))))
  res = (1,2,3,4,5,6,7)
~~~~~~~~

W skrócie, `Zip` i `Unzip` są słabszymi wersjami `Divide` i `Apply` dostarczającymi użyteczne funkcjonalności
bez zobowiązywania `F` do składania zbyt wielu obietnic.


### Optional

`Optional` to uogólnienie struktur danych, które mogą opcjonalnie zawierać jakąś wartość, np. `Option` lub `Either`.

Przypomnijmy, że `\/` (*dysjunkcja*) ze Scalaz jest ulepszoną wersją `scala.Either`. Poznamy też `Maybe` - ulepszoną wersję
`scala.Option`.

{lang="text"}
~~~~~~~~
  sealed abstract class Maybe[A]
  final case class Empty[A]()    extends Maybe[A]
  final case class Just[A](a: A) extends Maybe[A]
~~~~~~~~

{lang="text"}
~~~~~~~~
  @typeclass trait Optional[F[_]] {
    def pextract[B, A](fa: F[A]): F[B] \/ A
  
    def getOrElse[A](fa: F[A])(default: =>A): A = ...
    def orElse[A](fa: F[A])(alt: =>F[A]): F[A] = ...
  
    def isDefined[A](fa: F[A]): Boolean = ...
    def nonEmpty[A](fa: F[A]): Boolean = ...
    def isEmpty[A](fa: F[A]): Boolean = ...
  
    def toOption[A](fa: F[A]): Option[A] = ...
    def toMaybe[A](fa: F[A]): Maybe[A] = ...
  }
~~~~~~~~

Powyższe metody powinny wydawać się znajome, może z wyjątkiem `pextract`, która pozwala `F[_]` na zwrócenie 
przechowywanej wartości lub specyficznego dla implementacji `F[B]`. Na przykład `Optional[Option].pextract` zwróci
nam `Option[Nothing] \/ A`, czyli `None \/ A`.

Scalaz daje nam operator trenarny dla wszystkich typów mających swoją instancję `Optional`.

{lang="text"}
~~~~~~~~
  implicit class OptionalOps[F[_]: Optional, A](fa: F[A]) {
    def ?[X](some: =>X): Conditional[X] = new Conditional[X](some)
    final class Conditional[X](some: =>X) {
      def |(none: =>X): X = if (Optional[F].isDefined(fa)) some else none
    }
  }
~~~~~~~~

Przykład:

{lang="text"}
~~~~~~~~
  scala> val knock_knock: Option[String] = ...
         knock_knock ? "who's there?" | "<tumbleweed>"
~~~~~~~~


## Ko-rzeczy

*Ko-rzecz* zazwyczaj ma sygnaturę przeciwną do tego co robi *rzecz*, ale nie musi koniecznie być jej odwrotnością.
Aby podkreślić relacje między *rzeczą* i *ko-rzeczą*, wszędzie gdzie to możliwe zawrzemy obie sygnatury.

{width=100%}
![](images/scalaz-cothings.png)

{width=80%}
![](images/scalaz-coloners.png)


### Cobind

{lang="text"}
~~~~~~~~
  @typeclass trait Cobind[F[_]] extends Functor[F] {
    def cobind[A, B](fa: F[A])(f: F[A] => B): F[B]
  //def   bind[A, B](fa: F[A])(f: A => F[B]): F[B]
  
    def cojoin[A](fa: F[A]): F[F[A]] = ...
  //def   join[A](ffa: F[F[A]]): F[A] = ...
  }
~~~~~~~~

`cobind` (znany również jako `coflatmap`) przyjmuje funkcję `F[A] => B`, która operuje na `F[A]` a nie jego elementach.
Ale nie zawsze będzie to pełne `fa`, często jest to substruktura stworzona przez metodę`cojoin` (znaną również jako 
`coflatten`), która rozwija strukturę danych.

Przekonywające przykłady użycia `Cobind` są rzadkie, jednak kiedy spojrzymy na tabele permutacji metod typeklasy `Functor`
ciężko jest uzasadnić czemu niektóre metody miałyby być ważniejsze od innych.

| method      | parameter          |
|------------ |------------------- |
| `map`       | `A => B`           |
| `contramap` | `B => A`           |
| `xmap`      | `(A => B, B => A)` |
| `ap`        | `F[A => B]`        |
| `bind`      | `A => F[B]`        |
| `cobind`    | `F[A] => B`        |


### Comonad

{lang="text"}
~~~~~~~~
  @typeclass trait Comonad[F[_]] extends Cobind[F] {
    def copoint[A](p: F[A]): A
  //def   point[A](a: =>A): F[A]
  }
~~~~~~~~

`.copoint` (znany też jako `.copure`) wydostaje element z kontekstu. Efekty z reguły nie posiadają instancji 
tej typeklasy, gdyż na przykład interpretacja `IO[A]` do `A` zaburza transparentność referencyjną. 
Dla struktur danych jednakże może to być na przykład wygodny sposób na pokazanie wszystkich elementów wraz z ich sąsiadami.

Rozważmy strukturę *sąsiedztwo* (`Hood`), która zawiera pewien element (`focus`) oraz elementy na 
lewo i prawo od niego (`lefts` i `rights`).

{lang="text"}
~~~~~~~~
  final case class Hood[A](lefts: IList[A], focus: A, rights: IList[A])
~~~~~~~~

`lefts` i `right` powinny być uporządkowane od najbliższego do najdalszego elementu względem elementu środkowego `focus`,
tak abyśmy mogli przekonwertować taką strukturę do `IList` za pomocą poniższej implementacji

{lang="text"}
~~~~~~~~
  object Hood {
    implicit class Ops[A](hood: Hood[A]) {
      def toIList: IList[A] = hood.lefts.reverse ::: hood.focus :: hood.rights
~~~~~~~~

Możemy zaimplementować metody do poruszania się w lewo (`previous`) i w prawo (`next`)

{lang="text"}
~~~~~~~~
  ...
      def previous: Maybe[Hood[A]] = hood.lefts match {
        case INil() => Empty()
        case ICons(head, tail) =>
          Just(Hood(tail, head, hood.focus :: hood.rights))
      }
      def next: Maybe[Hood[A]] = hood.rights match {
        case INil() => Empty()
        case ICons(head, tail) =>
          Just(Hood(hood.focus :: hood.lefts, head, tail))
      }
~~~~~~~~

Wprowadzając metodę `more` jesteśmy w stanie obliczyć *wszystkie* możliwe do osiągnięcia pozycje (`positions`) w danym `Hood`. 

{lang="text"}
~~~~~~~~
  ...
      def more(f: Hood[A] => Maybe[Hood[A]]): IList[Hood[A]] =
        f(hood) match {
          case Empty() => INil()
          case Just(r) => ICons(r, r.more(f))
        }
      def positions: Hood[Hood[A]] = {
        val left  = hood.more(_.previous)
        val right = hood.more(_.next)
        Hood(left, hood, right)
      }
    }
~~~~~~~~

Możemy teraz stworzyć `Comonad[Hood]`

{lang="text"}
~~~~~~~~
  ...
    implicit val comonad: Comonad[Hood] = new Comonad[Hood] {
      def map[A, B](fa: Hood[A])(f: A => B): Hood[B] =
        Hood(fa.lefts.map(f), f(fa.focus), fa.rights.map(f))
      def cobind[A, B](fa: Hood[A])(f: Hood[A] => B): Hood[B] =
        fa.positions.map(f)
      def copoint[A](fa: Hood[A]): A = fa.focus
    }
  }
~~~~~~~~

`cojoin` daje nam `Hood[Hood[IList]]` zawierające wszystkie możliwe sąsiedztwa w naszej początkowej liście

{lang="text"}
~~~~~~~~
  scala> val middle = Hood(IList(4, 3, 2, 1), 5, IList(6, 7, 8, 9))
  scala> middle.cojoin
  res = Hood(
          [Hood([3,2,1],4,[5,6,7,8,9]),
           Hood([2,1],3,[4,5,6,7,8,9]),
           Hood([1],2,[3,4,5,6,7,8,9]),
           Hood([],1,[2,3,4,5,6,7,8,9])],
          Hood([4,3,2,1],5,[6,7,8,9]),
          [Hood([5,4,3,2,1],6,[7,8,9]),
           Hood([6,5,4,3,2,1],7,[8,9]),
           Hood([7,6,5,4,3,2,1],8,[9]),
           Hood([8,7,6,5,4,3,2,1],9,[])])
~~~~~~~~

Okazuje się, że `cojoin` to tak naprawdę `positions`! A więc możemy nadpisać ją używając bezpośredniej 
(a przez to wydajniejszej) implementacji

{lang="text"}
~~~~~~~~
  override def cojoin[A](fa: Hood[A]): Hood[Hood[A]] = fa.positions
~~~~~~~~

`Comonad` generalizuje koncepcję sąsiedztwa dla arbitralnych struktur danych. `Hood` jest przykładem *zippera* 
(brak związku z `Zip`). Scalaz definiuje typ danych `Zipper` dla strumieni (jednowymiarowych nieskończonych struktur danych),
które omówimy w następnym rozdziale.

Jednym z zastosowanie zippera jest *automat komórkowy* (_cellular automata_), który wylicza wartość każdej komórki
w następnej generacji na podstawie aktualnych wartości sąsiadów tej komórki.

### Cozip

{lang="text"}
~~~~~~~~
  @typeclass trait Cozip[F[_]] {
    def cozip[A, B](x: F[A \/ B]): F[A] \/ F[B]
  //def   zip[A, B](a: =>F[A], b: =>F[B]): F[(A, B)]
  //def unzip[A, B](a: F[(A, B)]): (F[A], F[B])
  
    def cozip3[A, B, C](x: F[A \/ (B \/ C)]): F[A] \/ (F[B] \/ F[C]) = ...
    ...
    def cozip7[A ... H](x: F[(A \/ (... H))]): F[A] \/ (... F[H]) = ...
  }
~~~~~~~~

Mimo że nazwa tej typeklasy brzmi `Cozip`, lepiej jest spojrzeć na jej symetrię względem metody `unzip`.
Tam gdzie `unzip` zamienia `F[_]` zawierające produkt (tuple) na produkt zawierający `F[_]`, tam
tam `cozip` zamienia `F[_]` zawierające koprodukty (dysjunkcje) na koprodukt zawierający `F[_]`.


## Bi-rzeczy

Czasem mamy do czynienia z typami które przyjmują dwa parametry typu i chcielibyśmy prze`map`ować obie jego
strony. Możemy na przykład śledzić błędy po lewej stronie `Either` i chcieć przetransformować
wiadomości z tychże błędów.

Typeklasy `Functor` / `Foldable` / `Traverse` mają swoich krewnych, którzy pozwalają nam mapować obie strony wspieranych typów.

{width=30%}
![](images/scalaz-bithings.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Bifunctor[F[_, _]] {
    def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D]
  
    @op("<-:") def leftMap[A, B, C](fab: F[A, B])(f: A => C): F[C, B] = ...
    @op(":->") def rightMap[A, B, D](fab: F[A, B])(g: B => D): F[A, D] = ...
    @op("<:>") def umap[A, B](faa: F[A, A])(f: A => B): F[B, B] = ...
  }
  
  @typeclass trait Bifoldable[F[_, _]] {
    def bifoldMap[A, B, M: Monoid](fa: F[A, B])(f: A => M)(g: B => M): M
  
    def bifoldRight[A,B,C](fa: F[A, B], z: =>C)(f: (A, =>C) => C)(g: (B, =>C) => C): C
    def bifoldLeft[A,B,C](fa: F[A, B], z: C)(f: (C, A) => C)(g: (C, B) => C): C = ...
  
    def bifoldMap1[A, B, M: Semigroup](fa: F[A,B])(f: A => M)(g: B => M): Option[M] = ...
  }
  
  @typeclass trait Bitraverse[F[_, _]] extends Bifunctor[F] with Bifoldable[F] {
    def bitraverse[G[_]: Applicative, A, B, C, D](fab: F[A, B])
                                                 (f: A => G[C])
                                                 (g: B => G[D]): G[F[C, D]]
  
    def bisequence[G[_]: Applicative, A, B](x: F[G[A], G[B]]): G[F[A, B]] = ...
  }
~~~~~~~~

A> `<-:` i `:->` to szczęśliwe operatory (_happy operators_)!

Mimo że sygnatury metod są dość rozwlekłe, to są to niemal dokładnie te same metody które znamy 
z typeklas `Functor`, `Foldable` i `Traverse`, z tą różnicą że przyjmują dwie funkcje zamiast jednej. 
Czasami funkcje te muszą zwracać ten sam typ aby wyniki można było połączyć za pomocą `Monoid`u lub `Semigroup`y.

{lang="text"}
~~~~~~~~
  scala> val a: Either[String, Int] = Left("fail")
         val b: Either[String, Int] = Right(13)
  
  scala> b.bimap(_.toUpperCase, _ * 2)
  res: Either[String, Int] = Right(26)
  
  scala> a.bimap(_.toUpperCase, _ * 2)
  res: Either[String, Int] = Left(FAIL)
  
  scala> b :-> (_ * 2)
  res: Either[String,Int] = Right(26)
  
  scala> a :-> (_ * 2)
  res: Either[String, Int] = Left(fail)
  
  scala> { s: String => s.length } <-: a
  res: Either[Int, Int] = Left(4)
  
  scala> a.bifoldMap(_.length)(identity)
  res: Int = 4
  
  scala> b.bitraverse(s => Future(s.length), i => Future(i))
  res: Future[Either[Int, Int]] = Future(<not completed>)
~~~~~~~~

Dodatkowo możemy wrócić na chwile do `MonadPlus` (czyli `Monad`y z metodami `filterWith` i `unite`) aby zobaczyć,
że potrafi ona rozdzielać (`separate`) zawartość `Monad`y, jeśli tylko jej typ ma instancję `Bifoldable`.

{lang="text"}
~~~~~~~~
  @typeclass trait MonadPlus[F[_]] {
    ...
    def separate[G[_, _]: Bifoldable, A, B](value: F[G[A, B]]): (F[A], F[B]) = ...
    ...
  }
~~~~~~~~

Jest to bardzo przydatny mechanizm kiedy mamy do czynienia z kolekcją bi-rzeczy i chcemy podzielić ją
na kolekcję `A` i kolekcję `B`.

{lang="text"}
~~~~~~~~
  scala> val list: List[Either[Int, String]] =
           List(Right("hello"), Left(1), Left(2), Right("world"))
  
  scala> list.separate
  res: (List[Int], List[String]) = (List(1, 2), List(hello, world))
~~~~~~~~


## Podsumowanie

Dużo tego! Właśnie odkryliśmy standardową bibliotekę polimorficznych funkcjonalności. Ale patrząc na to z innej perspektywy:
w Collections API z biblioteki standardowej Scali jest więcej traitów niż typeklas w Scalaz.

To całkiem normalne, jeśli twoja czysto funkcyjna aplikacja korzysta jedynie z małej części omówionych typeklas,
a większość funkcjonalności czerpie z typeklas i algebr domenowych. Nawet jeśli twoje domenowe typeklasy są
tylko wyspecjalizowanymi odpowiednikami tych zdefiniowanych w Scalaz, to jest zupełnie ok aby zrefaktorować je
później.

Aby pomóc, dołączyliśmy cheat-sheet wszystkich typeklas i ich głównych metod w załączniku. Jest on zainspirowany przez 
[Scalaz Cheatsheet](http://arosien.github.io/scalaz-cheatsheets/typeclasses.pdf) Adama Rosiena.

Aby pomóc jeszcze bardziej, Valentin Kasas pokazuję jak  [połączyć `N` rzeczy](https://twitter.com/ValentinKasas/status/879414703340081156)

{width=70%}
![](images/shortest-fp-book.png)


# Typy Danych ze Scalaz

Kto nie kocha porządnej struktury danych? Odpowiedź brzmi *nikt*, a struktury danych są super!

W tym rozdziale poznamy typy danych przypominające kolekcje oraz takie, które wzbogacają Scalę o dodatkowe 
możliwości i zwiększają bezpieczeństwo typów.

Podstawowym powodem, dla którego używamy wielu różnych typów kolekcji jest wydajność. Wektor i lista mogą
zrobić to samo, ale ich charakterystyki wydajnościowe są inne: wektor oferuje dostęp do losowego elementu w czasie stałym
gdy lista musi zostać w czasie tej operacji przetrawersowana.

W> Szacunki wydajnościowe, wliczając w to twierdzenia w tym rozdziale, powinny być zawsze brane z przymrużeniem oka. 
W> Nowoczesne architektury procesorów, pipelining i garbage collector w JVMie mogą zaburzyć nasze intuicyjne wyliczenia
W> bazujące na zliczaniu wykonywanych operacji.
W> 
W> Gorzka prawda o współczesnych komputerach jest taka, że empiryczne testy wydajnościowe mogą szokować i zaskakiwać.
W> Przykładem może być to, że w praktyce `List`a jest często szybsza niż `Vector`. Jeśli badasz wydajność, używaj
W> narzędzi takich jak [JMH](http://openjdk.java.net/projects/code-tools/jmh/).

Wszystkie kolekcje, które tutaj zaprezentujemy są *trwałe* (_persistent_): jeśli dodamy lub usuniemy element, nadal możemy
używać poprzedniej, niezmienionej wersji. Współdzielenie strukturalne (_structural sharing_) jest kluczowe dla 
wydajności trwałych struktur danych, bez tego musiałyby one być tworzone od nowa przy każdej operacji.

W przeciwieństwie do kolekcji z bibliotek standardowych Javy i Scali, w Scalaz typy danych nie tworzą hierarchii, a przez to
są dużo prostsze do zrozumienia. Polimorfizm jest zapewniany przez zoptymalizowane instancje typeklas które poznaliśmy
w poprzednim rozdziale. Sprawia to, że zmiana implementacji podyktowana zwiększeniem wydajności, lub dostarczenie własnej, 
jest dużo prostsze.

## Wariancja typów

Wiele z typów danych zdefiniowanych w Scalaz jest *inwariantna*. Dla przykładu `IList[A]` **nie** jest podtypem
`IList[B]` nawet jeśli `A <: B`.

### Kowariancja

Problem z kowariantnymi parametrami typu, takimi jak `A` w `class List[+A]`, jest taki, że `List[A]` jest podtypem
(a więc dziedziczy po) `List[Any]` i bardzo łatwo jest przez przypadek zgubić informacje o typach.

{lang="text"}
~~~~~~~~
  scala> List("hello") ++ List(' ') ++ List("world!")
  res: List[Any] = List(hello,  , world!)
~~~~~~~~

Zauważ, że druga lista jest typu `List[Char]` i kompilator niezbyt pomocnie wyinferował `Any` jako 
*Najmniejszą Górną Granicę* (_Least Upper Bound_, LUB). Porównajmy to z `IList`, która wymaga bezpośredniego wywołania 
`.widen[Any]` aby pozwolić na ten haniebny uczynek:

{lang="text"}
~~~~~~~~
  scala> IList("hello") ++ IList(' ') ++ IList("world!")
  <console>:35: error: type mismatch;
   found   : Char(' ')
   required: String
  
  scala> IList("hello").widen[Any]
           ++ IList(' ').widen[Any]
           ++ IList("world!").widen[Any]
  res: IList[Any] = [hello, ,world!]
~~~~~~~~

Podobnie, gdy kompilator inferuje typ z dopiskiem `with Product with Serializable` to najprawdopodobniej 
miało miejsce przypadkowe rozszerzenie typu spowodowane kowariancją.

Niestety, musimy uważać nawet gdy konstruujemy typy inwariantne, ponieważ obliczenie LUB wykonywane jest również
dla parametrów typu:

{lang="text"}
~~~~~~~~
  scala> IList("hello", ' ', "world")
  res: IList[Any] = [hello, ,world]
~~~~~~~~

Kolejny podobny problem powodowany jest przez typ `Nothing`, który jest podtypem wszystkich innych typów, wliczając w to
ADT, klasy finalne, typy prymitywne oraz `null`.

Nie istnieją wartości typu `Nothing`.  Funkcje które przyjmują `Nothing` jako parametr nie mogą zostać uruchomione, a
funkcje które zwracają ten typ nigdy nie zwrócą rezultatu. Typ `Nothing` został wprowadzony aby umożliwić używanie 
kowariantnych parametrów typu, ale w konsekwencji umożliwił pisanie kodu który nie może być uruchomiony, często przez przypadek.
Scalaz twierdzi, że wcale nie potrzebujemy kowariantnych parametrów typu, ograniczając się tym samym do praktycznego
kodu, który może zostać uruchomiony.


### Sprzeciwwariancja 

Z drugiej strony, parametry *kontrawariantne* takie jak `A` w `trait Thing[-A]` mogą ujawnić niszczycielskie
[błędy w kompilatorze](https://issues.scala-lang.org/browse/SI-2509). Spójrzmy na to co Paul Phillips (były
członek zespołu pracującego nad `scalac`) nazywa *contrarivariance*:

{lang="text"}
~~~~~~~~
  scala> :paste
         trait Thing[-A]
         def f(x: Thing[ Seq[Int]]): Byte   = 1
         def f(x: Thing[List[Int]]): Short  = 2
  
  scala> f(new Thing[ Seq[Int]] { })
         f(new Thing[List[Int]] { })
  
  res = 1
  res = 2
~~~~~~~~

Tak jak byśmy oczekiwali, kompilator odnalazł najdokładniejsze dopasowanie metod do argumentów.
Sprawa komplikuje się jednak gdy użyjemy wartości niejawnych

{lang="text"}
~~~~~~~~
  scala> :paste
         implicit val t1: Thing[ Seq[Int]] =
           new Thing[ Seq[Int]] { override def toString = "1" }
         implicit val t2: Thing[List[Int]] =
           new Thing[List[Int]] { override def toString = "2" }
  
  scala> implicitly[Thing[ Seq[Int]]]
         implicitly[Thing[List[Int]]]
  
  res = 1
  res = 1
~~~~~~~~

Niejawne rozstrzyganie odwraca definicje "najbardziej dokładnego" dla typów kontrawariantnych, czyniąc je tym samym
kompletnie bezużytecznymi do reprezentacji typeklas i czegokolwiek co wymaga polimorficznych funkcjonalności. 
Zachowanie to zostało poprawione w Dotty.


### Ograniczenia podtypów

`scala.Option` ma metodę `.flatten`, która konwertuje `Option[Option[B]]` na `Option[B]`.
Niestety kompilator Scali nie pozwala nam na poprawne zapisanie sygnatury tej metody. 
Rozważmy poniższą implementację która pozornie wydaje się poprawna:

{lang="text"}
~~~~~~~~
  sealed abstract class Option[+A] {
    def flatten[B, A <: Option[B]]: Option[B] = ...
  }
~~~~~~~~

`A` wprowadzone w definicji `.flatten` przysłania `A` wprowadzone w definicji klasy. Tak więc jest to równoznaczne z

{lang="text"}
~~~~~~~~
  sealed abstract class Option[+A] {
    def flatten[B, C <: Option[B]]: Option[B] = ...
  }
~~~~~~~~

czyli nie do końca jest tym czego chcieliśmy.

Jako obejście tego problemu wprowadzono klasy `<:<` i `=:=` wraz z niejawnymi metodami, które zawsze tworzą
instancje dla poprawnych typów.

{lang="text"}
~~~~~~~~
  sealed abstract class <:<[-From, +To] extends (From => To)
  implicit def conforms[A]: A <:< A = new <:<[A, A] { def apply(x: A): A = x }
  
  sealed abstract class =:=[ From,  To] extends (From => To)
  implicit def tpEquals[A]: A =:= A = new =:=[A, A] { def apply(x: A): A = x }
~~~~~~~~

`=:=` może być użyty do wymuszenia aby dwa parametry typu były dokładnie takie same. `<:<` służy do wyrażenia
relacji podtypowania, pozwalając tym samym na implementację `.flatten` jako

{lang="text"}
~~~~~~~~
  sealed abstract class Option[+A] {
    def flatten[B](implicit ev: A <:< Option[B]): Option[B] = this match {
      case None        => None
      case Some(value) => ev(value)
    }
  }
  final case class Some[+A](value: A) extends Option[A]
  case object None                    extends Option[Nothing]
~~~~~~~~

Scalaz definiuje ulepszone wersje `<:<` i `=:=`: *Liskov* (z aliasem `<=<`) oraz *Leibniz* (`===`).

{lang="text"}
~~~~~~~~
  sealed abstract class Liskov[-A, +B] {
    def apply(a: A): B = ...
    def subst[F[-_]](p: F[B]): F[A]
  
    def andThen[C](that: Liskov[B, C]): Liskov[A, C] = ...
    def onF[X](fa: X => A): X => B = ...
    ...
  }
  object Liskov {
    type <~<[-A, +B] = Liskov[A, B]
    type >~>[+B, -A] = Liskov[A, B]
  
    implicit def refl[A]: (A <~< A) = ...
    implicit def isa[A, B >: A]: A <~< B = ...
  
    implicit def witness[A, B](lt: A <~< B): A => B = ...
    ...
  }
  
  // type signatures have been simplified
  sealed abstract class Leibniz[A, B] {
    def apply(a: A): B = ...
    def subst[F[_]](p: F[A]): F[B]
  
    def flip: Leibniz[B, A] = ...
    def andThen[C](that: Leibniz[B, C]): Leibniz[A, C] = ...
    def onF[X](fa: X => A): X => B = ...
    ...
  }
  object Leibniz {
    type ===[A, B] = Leibniz[A, B]
  
    implicit def refl[A]: Leibniz[A, A] = ...
  
    implicit def subst[A, B](a: A)(implicit f: A === B): B = ...
    implicit def witness[A, B](f: A === B): A => B = ...
    ...
  }
~~~~~~~~

Poza dostarczeniem przydatnych metod i niejawnych konwersji, `<=<` i `===` są bardziej pryncypialne niż ich odpowiedniki
z biblioteki standardowej.

A> Liskov zawdzięcza swą nazwę Barbarze Liskov, autorce *Zasady podstawienia Liskov*,która stała się fundamentem 
A> Programowania Zorientowanego Obiektowo.
A>
A> Gottfried Wilhelm Leibniz to człowiek który odkrył *wszystko* w 17 wieku. Wierzył w [Boga zwanego Monadą](https://en.wikipedia.org/wiki/Monad_(philosophy)).
A> Eugenio Moggi później reużył tej nazwy dla abstrakcji, którą znamy jako `scalaz.Monad`. Już nie Bóg, ale jeszcze nie śmiertelnik.

## Ewaluacja

Java to język o *ścisłej* (_strict_) ewaluacji: wszystkie parametry przekazane do metody muszę zostać wyewaluowane do 
*wartości* zanim metoda zostanie uruchomiona. Scala wprowadza pojęcie parametrów przekazywanych *przez nazwę* (_by-name_)
za pomocą składni `a: =>A`. Takie parametry opakowywane są w zero-argumentową funkcję, która jest wywoływana za każdym razem gdy odnosimy
się do `a`. Widzieliśmy tego typu parametry wielokrotnie gdy omawialiśmy typeklasy.

Scala pozwala również na ewaluacje wartości *na żądanie* za pomocą słowa kluczowego `lazy`: obliczenia są wykonywane najwyżej raz
aby wyprodukować wartość przy pierwszym użyciu. Niestety Scala nie wspiera ewaluacji *na żądanie* dla parametrów metod.

A> Jeśli obliczenie wartości `lazy val` wyrzuci wyjątek, to jest ono powtarzane za każdym kolejnym użyciem tej zmiennej.
A> Ponieważ wyjątki mogą zaburzać transparencje referencyjną, ograniczymy się do omawiania definicji `lazy val`, które
A> zawsze produkują poprawną wartość.

Scalaz formalizuje te trzy strategie ewaluacji za pomocą ADT

{lang="text"}
~~~~~~~~
  sealed abstract class Name[A] {
    def value: A
  }
  object Name {
    def apply[A](a: =>A) = new Name[A] { def value = a }
    ...
  }
  
  sealed abstract class Need[A] extends Name[A]
  object Need {
    def apply[A](a: =>A): Need[A] = new Need[A] {
      private lazy val value0: A = a
      def value = value0
    }
    ...
  }
  
  final case class Value[A](value: A) extends Need[A]
~~~~~~~~

Najsłabszą formą ewaluacji jest `Name`, która nie daje żadnych gwarancji obliczeniowych. Następna jest `Need` gwarantująca
ewaluację *najwyżej raz* (_at most once_). `Value` jest obliczana przed utworzeniem, gwarantując tym samym ewaluację 
*dokładnie raz* (_exactly once_).

Gdybyśmy chcieli być pedantyczni, moglibyśmy wrócić do wszystkich typeklas, które poznaliśmy do tej pory i zamienić przyjmowane 
parametry w ich metodach na `Name`, `Need` i `Value`. Zamiast tego możemy też po prostu założyć że normalne parametry
mogą być zawsze opakowane w `Value`, a te przekazywane *przez nazwę* w `Name`.

Gdy piszemy *czyste programy* możemy śmiało zamienić dowolne `Name` na `Need` lub `Value`, i vice versa, bez zmieniania
poprawności programu. To jest właśnie esencja *transparencji referencyjnej*: zdolność do zamiany obliczeń na wartość wynikową
lub wartości na obliczenia potrzebne do jej uzyskania.

W programowaniu funkcyjnym prawie zawsze potrzebujemy `Value` lub `Need` (znane też jako parametry *ścisłe* i *leniwe*), ale nie mamy
zbyt wiele pożytku z `Name`. Ponieważ na poziomie języka nie mamy bezpośredniego wsparcia dla leniwych parametrów, metody często
przyjmują wartości *przez nazwę* a następnie konwertują je do `Need`, zwiększając tym samym wydajność.

A> Typ `Lazy` (pisany z wielkiej litery) jest często używany w bibliotekach Scalowych do wyrażania semantyki przekazywania
A> *przez nazwę*. Jest to błąd w nazewnictwie, który już zdążył się zadomowić.
A> 
A> Ogólnie rzecz biorąc, dość leniwie podchodzimy do lenistwa. Czasem warto dociec jaki dokładnie rodzaj
A> leniwego zachowania omawiamy. Albo nie. Bo po co?

`Name` dostarcza instancje poniższych typeklas:

-   `Monad`
-   `Comonad`
-   `Traverse1`
-   `Align`
-   `Zip` / `Unzip` / `Cozip`

A> "Nie ma nic za darmo", mówi powiedzenie i tak samo jest z parametrami *leniwymi* i przekazywanymi *przez nazwę*.
A> Kiedy Scala konwertuje te parametry do kodu bajtowego pojawia się narzut związany z dodatkową alokacją pamięci.
A> 
A> Zanim przepiszesz wszystko z użyciem parametrów przekazywanych *przez nazwę*, upewnij się że koszt nie przyćmiewa zysków.
A> Nie ma żadnej wartości dodanej, jeśli nie istnieje możliwość **nie** ewaluowania danej wartości. Kod wymagający 
A> wysokiej wydajności uruchomiony w ścisłej pętli i zawsze wymagający konkretnej wartości danego parametru znacząco ucierpi na takim 
A> refactoringu.

## Memoizacja

Scalaz potrafi memoizować funkcje za pomocą typu `Memo`, który nie daje żadnych gwarancji co do ewaluacji z powodu
dużej gamy różniących się implementacji:

{lang="text"}
~~~~~~~~
  sealed abstract class Memo[K, V] {
    def apply(z: K => V): K => V
  }
  object Memo {
    def memo[K, V](f: (K => V) => K => V): Memo[K, V]
  
    def nilMemo[K, V]: Memo[K, V] = memo[K, V](identity)
  
    def arrayMemo[V >: Null : ClassTag](n: Int): Memo[Int, V] = ...
    def doubleArrayMemo(n: Int, sentinel: Double = 0.0): Memo[Int, Double] = ...
  
    def immutableHashMapMemo[K, V]: Memo[K, V] = ...
    def immutableTreeMapMemo[K: scala.Ordering, V]: Memo[K, V] = ...
  }
~~~~~~~~

`memo` pozwala nam na tworzenie własnych implementacji `Memo`. `nilMemo` nie memoizuje w ogóle, a więc funkcja wykonywana 
jest za każdym wywołaniem. Pozostałe implementacje przechwytują wywołania funkcji i cache'ują wynik przy użyciu kolekcji z 
biblioteki standardowej.

Aby wykorzystać `Memo` wystarczy że opakujemy naszą funkcje z użyciem wybranej implementacji, a następnie używać będziemy
zwróconej nam funkcji zamiast tej oryginalnej:

{lang="text"}
~~~~~~~~
  scala> def foo(n: Int): String = {
           println("running")
           if (n > 10) "wibble" else "wobble"
         }
  
  scala> val mem = Memo.arrayMemo[String](100)
         val mfoo = mem(foo)
  
  scala> mfoo(1)
  running // evaluated
  res: String = wobble
  
  scala> mfoo(1)
  res: String = wobble // memoised
~~~~~~~~

Jeśli funkcja przyjmuje więcej niż jeden argument musimy wywołać na niej `tupled` konwertując ją tym samym
do jednoargumentowej funkcji przyjmującej tuple.

{lang="text"}
~~~~~~~~
  scala> def bar(n: Int, m: Int): String = "hello"
         val mem = Memo.immutableHashMapMemo[(Int, Int), String]
         val mbar = mem((bar _).tupled)
  
  scala> mbar((1, 2))
  res: String = "hello"
~~~~~~~~

`Memo` jest traktowany w specjalny sposób i typowe reguły *czystości* są nieco osłabione przy jego implementacji.
Aby nosić miano czystego wystarczy aby wykonanie `K => V` było referencyjnie transparentne. Przy implementacji możemy
używać mutowalnych struktur danych lub wykonywać operacje I/O, np. aby uzyskać LRU lub rozproszony cache bez deklarowania efektów
w sygnaturze typu. Inne funkcyjne języki programowania udostępniają automatyczną memoizację zarządzaną przez środowisko 
uruchomieniowe, `Memo` to nasz sposób na dodanie podobnej funkcjonalności do JVMa, niestety jedynie jako "opt-in".


## Tagowanie

W podrozdziale wprowadzającym `Monoid` stworzyliśmy `Monoid[TradeTemplate]` jednocześnie uświadamiając sobie, że
domyślna instancja `Monoid[Option[A]]` ze Scalaz nie robi tego czego byśmy od niej oczekiwali. Nie jest to jednak przeoczenie
ze strony Scalaz: często będziemy napotykali sytuację w której dany typ danych może mieć wiele poprawnych implementacji
danej typeklasy a ta domyślna nie robi tego czego byśmy chcieli lub w ogóle nie jest zdefiniowana.

Najprostszym przykładem jest `Monoid[Boolean]` (koniunkcja `&&` vs alternatywa `||`) lub `Monoid[Int]` (mnożenie vs dodawanie).

Aby zaimplementować `Monoid[TradeTemplate]` musieliśmy albo zaburzyć spójność typeklas albo użyć innej typeklasy niż `Monoid`.

`scalaz.Tag` został zaprojektowany jako rozwiązanie tego problemu, ale bez sprowadzania ograniczeń, które napotkaliśmy.

Definicja jest dość pokrzywiona, ale składnia dostarczana użytkownikowi jest bardzo przejrzysta. Oto w jaki sposób możemy
oszukać kompilator i zdefiniować typ `A @@ T`, który zostanie uproszczony do `A` w czasie wykonania programu:

{lang="text"}
~~~~~~~~
  type @@[A, T] = Tag.k.@@[A, T]
  
  object Tag {
    @inline val k: TagKind = IdTagKind
    @inline def apply[A, T](a: A): A @@ T = k(a)
    ...
  
    final class TagOf[T] private[Tag]() { ... }
    def of[T]: TagOf[T] = new TagOf[T]
  }
  sealed abstract class TagKind {
    type @@[A, T]
    def apply[A, T](a: A): A @@ T
    ...
  }
  private[scalaz] object IdTagKind extends TagKind {
    type @@[A, T] = A
    @inline override def apply[A, T](a: A): A = a
    ...
  }
~~~~~~~~

A> A więc tagujemy rzeczy używając koków księżniczki Lei: `@@`.

Kilka użytecznych tagów znajdziemy w obiekcie `Tags`

{lang="text"}
~~~~~~~~
  object Tags {
    sealed trait First
    val First = Tag.of[First]
  
    sealed trait Last
    val Last = Tag.of[Last]
  
    sealed trait Multiplication
    val Multiplication = Tag.of[Multiplication]
  
    sealed trait Disjunction
    val Disjunction = Tag.of[Disjunction]
  
    sealed trait Conjunction
    val Conjunction = Tag.of[Conjunction]
  
    ...
  }
~~~~~~~~

`First` i `Last` służą do wyboru między instancjami `Monoidu`, które wybierają odpowiednio pierwszy lub ostatni 
operand. Za pomocą `Multiplication` możemy zmienić zachowanie `Monoid`u dla typów liczbowych z dodawania na mnożenie.
`Disjunction` i `Conjunction` pozwalają wybrać między `&&` i `||` dla typu `Boolean`.

W naszym przykładzie definiującym `TradeTemplate`, zamiast `Option[Currency]` mogliśmy użyć `Option[Currency] @@ Tags.Last`.
W rzeczywistości jest to przypadek tak częsty, że mogliśmy użyć wbudowanego aliasu `LastOption`.  

{lang="text"}
~~~~~~~~
  type LastOption[A] = Option[A] @@ Tags.Last
~~~~~~~~

i tym samym sprawić, że implementacja `Monoid[TradeTemplate]` będzie znacznie czystsza

{lang="text"}
~~~~~~~~
  final case class TradeTemplate(
    payments: List[java.time.LocalDate],
    ccy: LastOption[Currency],
    otc: LastOption[Boolean]
  )
  object TradeTemplate {
    implicit val monoid: Monoid[TradeTemplate] = Monoid.instance(
      (a, b) =>
        TradeTemplate(a.payments |+| b.payments,
                      a.ccy |+| b.ccy,
                      a.otc |+| b.otc),
        TradeTemplate(Nil, Tag(None), Tag(None))
    )
  }
~~~~~~~~

Tworzymy wartości typu `LastOption` poprzez zaaplikowanie `Tag` na instancji `Option`. W tym wypadku wołamy
`Tag(None)`.

W rozdziale o derywacji typeklas pójdziemy o jeden krok dalej i stworzymy `monoid` automatycznie.

Kuszącym może wydać się pomysł użycia `Tag`ów do oznaczania danych na potrzeby walidacji (np. `String @@ PersonName`),
ale należy oprzeć się tym pokusom, gdyż za takim oznaczeniem nie stoją żadne weryfikacje wartości używanych w czasie wykonania.
`Tag` powinien być używany tylko do selekcji typeklas, a do ograniczania możliwych wartości dużo lepiej jest użyć 
biblioteki `Refined`, która poznaliśmy w Rozdziale 4.


## Transformacja Naturalna

Funkcja z jednego typu w drugi zapisywana jest w Scali jako `A => B`, ale jest to tylko syntax sugar dla 
`Function1[A, B]`. Scalaz dostarcza podobny mechanizm w formie `F ~> G` dla funkcji z konstruktora typu
`F[_]` do `G[_]`.

Te właśnie `F ~> G` nazywamy *transformacjami naturalnymi* (_natural transformation_) i są one 
*uniwersalnie kwantyfikowane*, ponieważ nie ma dla nich znaczenia zawartość `F[_]`.

{lang="text"}
~~~~~~~~
  type ~>[-F[_], +G[_]] = NaturalTransformation[F, G]
  trait NaturalTransformation[-F[_], +G[_]] {
    def apply[A](fa: F[A]): G[A]
  
    def compose[E[_]](f: E ~> F): E ~> G = ...
    def andThen[H[_]](f: G ~> H): F ~> H = ...
  }
~~~~~~~~

Przykładem transformacji naturalnej jest funkcja, która konwertuje `IList` na `List`

{lang="text"}
~~~~~~~~
  scala> val convert = new (IList ~> List) {
           def apply[A](fa: IList[A]): List[A] = fa.toList
         }
  
  scala> convert(IList(1, 2, 3))
  res: List[Int] = List(1, 2, 3)
~~~~~~~~

Lub, bardziej zwięźle, korzystając ze składni udostępnianej przez `kind-projector`:

{lang="text"}
~~~~~~~~
  scala> val convert = λ[IList ~> List](_.toList)
  
  scala> val convert = Lambda[IList ~> List](_.toList)
~~~~~~~~

Jednak w codziennej pracy zdecydowanie częściej będziemy używać transformacji naturalnych do konwersji między
algebrami. Możemy, na przykład, chcieć zaimplementować naszą algebrę `Machines`, służącą do komunikacji z Google Container
Engine, za pomocą gotowej, zewnętrznej algebry `BigMachines`. Zamiast zmieniać naszą logikę biznesową i wszystkie testy,
tak aby używały nowej algebry, możemy spróbować napisać transformację naturalną `BigMachines ~> Machines`. 
Powrócimy do tego pomysłu w rozdziale o Zaawansowanych Monadach.


## `Isomorphism`

Czasami mamy do czynienia z dwoma typami, które tak naprawdę są dokładnie tym samym.
Powoduje to problemy z kompatybilnością, ponieważ kompilator najczęściej nie ma tej wiedzy.
Najczęściej takie sytuacje mają miejsce gdy chcemy użyć zewnętrznych bibliotek, które definiują
coś co już mamy w naszym kodzie.

W takich właśnie okolicznościach z pomocą przychodzi `Isomorphism`, który definiuje relację równoznaczności
między dwoma typami. Ma on 3 warianty dla typów o różnym kształcie:

{lang="text"}
~~~~~~~~
  object Isomorphism {
    trait Iso[Arr[_, _], A, B] {
      def to: Arr[A, B]
      def from: Arr[B, A]
    }
    type IsoSet[A, B] = Iso[Function1, A, B]
    type <=>[A, B] = IsoSet[A, B]
    object IsoSet {
      def apply[A, B](to: A => B, from: B => A): A <=> B = ...
    }
  
    trait Iso2[Arr[_[_], _[_]], F[_], G[_]] {
      def to: Arr[F, G]
      def from: Arr[G, F]
    }
    type IsoFunctor[F[_], G[_]] = Iso2[NaturalTransformation, F, G]
    type <~>[F[_], G[_]] = IsoFunctor[F, G]
    object IsoFunctor {
      def apply[F[_], G[_]](to: F ~> G, from: G ~> F): F <~> G = ...
    }
  
    trait Iso3[Arr[_[_, _], _[_, _]], F[_, _], G[_, _]] {
      def to: Arr[F, G]
      def from: Arr[G, F]
    }
    type IsoBifunctor[F[_, _], G[_, _]] = Iso3[~~>, F, G]
    type <~~>[F[_, _], G[_, _]] = IsoBifunctor[F, G]
  
    ...
  }
~~~~~~~~

Aliasy typów `IsoSet`, `IsoFunctor` i `IsoBiFunctor` pokrywają najczęstsze przypadki: zwykłe funkcje,
transformacje naturalne i binaturalne. Funkcje pomocnicze pozwalają nam generować instancje `Iso` z gotowych
funkcji lub transformacji, ale często łatwiej jest użyć do tego klas `Template`. Na przykład:

{lang="text"}
~~~~~~~~
  val listIListIso: List <~> IList =
    new IsoFunctorTemplate[List, IList] {
      def to[A](fa: List[A]) = fromList(fa)
      def from[A](fa: IList[A]) = fa.toList
    }
~~~~~~~~

Jeśli wprowadzimy izomorfizm, możemy wygenerować wiele standardowych typeklas. Dla przykładu

{lang="text"}
~~~~~~~~
  trait IsomorphismSemigroup[F, G] extends Semigroup[F] {
    implicit def G: Semigroup[G]
    def iso: F <=> G
    def append(f1: F, f2: =>F): F = iso.from(G.append(iso.to(f1), iso.to(f2)))
  }
~~~~~~~~

pozwala nam wyderywować `Semigroup[F]` dla typu `F` jeśli mamy `F <=> G` oraz `Semigroup[G]`.
Niemal wszystkie typeklasy w hierarchii mają wariant dla typów izomorficznych. Jeśli złapiemy się na 
kopiowaniu implementacji danej typeklasy, warto rozważyć zdefiniowanie `Isomorphism`u.


## Kontenery


### Maybe

Widzieliśmy już `Maybe`, Scalazowe ulepszenie `scala.Option`. Jest to ulepszenie dzięki swojej inwariancji oraz braku
jakichkolwiek nieczystych metod, taki jak `Option.get`, które mogą rzucać wyjątki.

Zazwyczaj typ ten używany jest do reprezentacji rzeczy, które mogą być nieobecne, bez podawania żadnej przyczyny
ani wyjaśnienia dla tej nieobecności.

{lang="text"}
~~~~~~~~
  sealed abstract class Maybe[A] { ... }
  object Maybe {
    final case class Empty[A]()    extends Maybe[A]
    final case class Just[A](a: A) extends Maybe[A]
  
    def empty[A]: Maybe[A] = Empty()
    def just[A](a: A): Maybe[A] = Just(a)
  
    def fromOption[A](oa: Option[A]): Maybe[A] = ...
    def fromNullable[A](a: A): Maybe[A] = if (null == a) empty else just(a)
    ...
  }
~~~~~~~~

`.empty` i `.just` są lepsze niż tworzenie `Just` i `Maybe` bezpośrednio, ponieważ zwracają `Maybe` pomagając tym samym
w inferencji typów. Takie podejście często nazywane jest zwracaniem typu sumy (_sum type_), a więc mimo posiadania
wielu implementacji zapieczętowanego traita (_sealed trait_) nigdy nie używamy konkretnych podtypów w sygnaturach metod.

Pomocnicza klasa niejawna pozwala nam zawołać `.just` na dowolnej wartości i uzyskać `Maybe`.

{lang="text"}
~~~~~~~~
  implicit class MaybeOps[A](self: A) {
    def just: Maybe[A] = Maybe.just(self)
  }
~~~~~~~~

`Maybe` posiada instancje wszystkich poniższych typeklas

-   `Align`
-   `Traverse`
-   `MonadPlus` / `IsEmpty`
-   `Cobind`
-   `Cozip` / `Zip` / `Unzip`
-   `Optional`

oraz deleguje implementację poniższych do instancji dla typu `A`

-   `Monoid` / `Band`
-   `Equal` / `Order` / `Show`

Dodatkowo, `Maybe` oferuje funkcjonalności nie dostępne w żadnej typeklasie

{lang="text"}
~~~~~~~~
  sealed abstract class Maybe[A] {
    def cata[B](f: A => B, b: =>B): B = this match {
      case Just(a) => f(a)
      case Empty() => b
    }
  
    def |(a: =>A): A = cata(identity, a)
    def toLeft[B](b: =>B): A \/ B = cata(\/.left, \/-(b))
    def toRight[B](b: =>B): B \/ A = cata(\/.right, -\/(b))
    def <\/[B](b: =>B): A \/ B = toLeft(b)
    def \/>[B](b: =>B): B \/ A = toRight(b)
  
    def orZero(implicit A: Monoid[A]): A = getOrElse(A.zero)
    def orEmpty[F[_]: Applicative: PlusEmpty]: F[A] =
      cata(Applicative[F].point(_), PlusEmpty[F].empty)
    ...
  }
~~~~~~~~

`.cata` to zwięźlejsza alternatywa dla `.map(f).getOrElse(b)` dostępna również po postacią `|` jeśli `f` to `identity`
(co jest równoznaczne z `.getOrElse`).

`.toLeft` i `.toRight` oraz ich aliasy symbolicznie tworzą dysjunkcje (opisane w następnym podrozdziale) przyjmując
wartość używaną w przypadku napotkania `Empty`.

`.orZero` używa instancji typeklasy `Monoid` do zdobycia wartości domyślnej.

`.orEmpty` używa `ApplicativePlus` aby stworzyć jednoelementowy lub pusty kontener. Pamiętajmy że podobną funkcjonalność
dla kolekcji udostępnia nam `.to` pochodzące z `Foldable`.

{lang="text"}
~~~~~~~~
  scala> 1.just.orZero
  res: Int = 1
  
  scala> Maybe.empty[Int].orZero
  res: Int = 0
  
  scala> Maybe.empty[Int].orEmpty[IList]
  res: IList[Int] = []
  
  scala> 1.just.orEmpty[IList]
  res: IList[Int] = [1]
  
  scala> 1.just.to[List] // from Foldable
  res: List[Int] = List(1)
~~~~~~~~

A> Metody definiowane są tutaj w stylu OOP, a nie używając `object` lub `implicit class` jak uczyliśmy się w Rozdziale 4.
A> Jest to częste zjawisko w Scalaz i wynika w dużej mierze z zaszłości historycznych:
A> 
A> -   edytory tekstu miały tendencję do nie znajdywania metod rozszerzających, ale teraz działa to bez żadnego
A>     problemu w IntelliJ, ENSIME i ScalaIDE.
A> -   istnieją rzadkie przypadki, w których kompilator nie potrafi poprawie wyinferować typu, a więc i znaleźć
A>     metod rozszerzających
A> -   biblioteka standardowa definiuje kilka instancji klas niejawnych, które dodają metody do wszystkich wartości, często
A>     z konfliktującymi nazwami. Czołowym przykładem jest `+`, który zamienia wszystko w skonkatenowany `String`.
A> 
A> To samo zachodzi również dla funkcjonalności dostarczanych przez typeklasy, jak na przykład `Optional`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   sealed abstract class Maybe[A] {
A>     def getOrElse(a: =>A): A = ...
A>     ...
A>   }
A> ~~~~~~~~
A> 
A> Jednak niedawne wersje Scali rozwiązały wiele błędów i szansa na napotkanie problemów jest dużo mniejsza.


### Either

Typ ulepszający `scala.Either` w Scalaz jest symbolem, ale przyjęło się nazywać go *either* lub `Disjunction`.

{lang="text"}
~~~~~~~~
  sealed abstract class \/[+A, +B] { ... }
  final case class -\/[+A](a: A) extends (A \/ Nothing)
  final case class \/-[+B](b: B) extends (Nothing \/ B)
  
  type Disjunction[+A, +B] = \/[A, B]
  
  object \/ {
    def left [A, B]: A => A \/ B = -\/(_)
    def right[A, B]: B => A \/ B = \/-(_)
  
    def fromEither[A, B](e: Either[A, B]): A \/ B = ...
    ...
  }
~~~~~~~~

z odpowiednią składnią

{lang="text"}
~~~~~~~~
  implicit class EitherOps[A](val self: A) {
    final def left [B]: (A \/ B) = -\/(self)
    final def right[B]: (B \/ A) = \/-(self)
  }
~~~~~~~~

pozwalającą na łatwe tworzenie wartości. Zauważ, że te metody przyjmują typ *drugiej strony* jako parametr. A więc
jeśli chcesz stworzyć `String \/ Int` mając `Int`, wołając `.right` musisz przekazać `String`.

{lang="text"}
~~~~~~~~
  scala> 1.right[String]
  res: String \/ Int = \/-(1)
  
  scala> "hello".left[Int]
  res: String \/ Int = -\/(hello)
~~~~~~~~

Symboliczna natura `\/` sprawia, że dobrze się go czyta, gdy użyty jest jako infiks. Pamiętaj, że typy symboliczne w Scali
wiążą argumenty od lewej, a więc zagnieżdżone `\/` muszą być ujęte w nawiasy.

`\/` posiada prawostronne (czyli `flatMap` wykonywany jest na `\/-`) instancje typeklas:

-   `Monad` / `MonadError`
-   `Traverse` / `Bitraverse`
-   `Plus`
-   `Optional`
-   `Cozip`

oraz zależne od zawartości

-   `Equal` / `Order`
-   `Semigroup` / `Monoid` / `Band`

Dodatkowo dostajemy kilka niestandardowych metod

{lang="text"}
~~~~~~~~
  sealed abstract class \/[+A, +B] { self =>
    def fold[X](l: A => X, r: B => X): X = self match {
      case -\/(a) => l(a)
      case \/-(b) => r(b)
    }
  
    def swap: (B \/ A) = self match {
      case -\/(a) => \/-(a)
      case \/-(b) => -\/(b)
    }
  
    def |[BB >: B](x: =>BB): BB = getOrElse(x) // Optional[_]
    def |||[C, BB >: B](x: =>C \/ BB): C \/ BB = orElse(x) // Optional[_]
  
    def +++[AA >: A: Semigroup, BB >: B: Semigroup](x: =>AA \/ BB): AA \/ BB = ...
  
    def toEither: Either[A, B] = ...
  
    final class SwitchingDisjunction[X](right: =>X) {
      def <<?:(left: =>X): X = ...
    }
    def :?>>[X](right: =>X) = new SwitchingDisjunction[X](right)
    ...
  }
~~~~~~~~

`.fold` przypomina `Maybe.cata` i wymaga aby obie strony zostały przemapowane do tego samego typu.

`.swap` zamienia strony miejscami, lewa na prawo, prawa na lewo.

Alias `|` na `getOrElse` jest podobny do tego w `Maybe`. Dostajemy też `|||` jako alias na `orElse`. 

`+++` pozwala na łączenie dysjunkcji priorytetyzując te, które wypełnione są po lewej stronie.

-   `right(v1) +++ right(v2)` gives `right(v1 |+| v2)`
-   `right(v1) +++ left (v2)` gives `left (v2)`
-   `left (v1) +++ right(v2)` gives `left (v1)`
-   `left (v1) +++ left (v2)` gives `left (v1 |+| v2)`

`.toEither` zapewnia kompatybilność z biblioteką standardową.

Połączenie `:?>>` i `<<?:` pozwala w wygodny sposób zignorować zawartość `\/` wybierając jednocześnie nową wartość 
zależnie od jego typu.

{lang="text"}
~~~~~~~~
  scala> 1 <<?: foo :?>> 2
  res: Int = 2 // foo is a \/-
  
  scala> 1 <<?: foo.swap :?>> 2
  res: Int = 1
~~~~~~~~


### Validation

Na pierwszy rzut oka `Validation` (zaliasowana jako `\?/` czyli *szczęśliwy Elvis*) wydaje się być klonem
`Disjunction`:

{lang="text"}
~~~~~~~~
  sealed abstract class Validation[+E, +A] { ... }
  final case class Success[A](a: A) extends Validation[Nothing, A]
  final case class Failure[E](e: E) extends Validation[E, Nothing]
  
  type ValidationNel[E, +X] = Validation[NonEmptyList[E], X]
  
  object Validation {
    type \?/[+E, +A] = Validation[E, A]
  
    def success[E, A]: A => Validation[E, A] = Success(_)
    def failure[E, A]: E => Validation[E, A] = Failure(_)
    def failureNel[E, A](e: E): ValidationNel[E, A] = Failure(NonEmptyList(e))
  
    def lift[E, A](a: A)(f: A => Boolean, fail: E): Validation[E, A] = ...
    def liftNel[E, A](a: A)(f: A => Boolean, fail: E): ValidationNel[E, A] = ...
    def fromEither[E, A](e: Either[E, A]): Validation[E, A] = ...
    ...
  }
~~~~~~~~

Z pomocną składnią

{lang="text"}
~~~~~~~~
  implicit class ValidationOps[A](self: A) {
    def success[X]: Validation[X, A] = Validation.success[X, A](self)
    def successNel[X]: ValidationNel[X, A] = success
    def failure[X]: Validation[A, X] = Validation.failure[A, X](self)
    def failureNel[X]: ValidationNel[A, X] = Validation.failureNel[A, X](self)
  }
~~~~~~~~

Jednak sama struktura danych to nie wszystko. `Validation` celowo nie posiada instancji `Monad`,
ograniczając się do:

-   `Applicative`
-   `Traverse` / `Bitraverse`
-   `Cozip`
-   `Plus`
-   `Optional`

oraz zależnych od zawartości:

-   `Equal` / `Order`
-   `Show`
-   `Semigroup` / `Monoid`

Dużą zaletą ograniczenia się do `Applicative` jest to, że `Validation` używany jest wyraźnie w sytuacjach,
w których chcemy zebrać wszystkie napotkane problemy, natomiast `Disjunction` zatrzymuje się przy pierwszym i ignoruje
pozostałe. Aby wesprzeć akumulacje błędów mamy do dyspozycji `ValidationNel`, czyli `Validation` z `NonEmptyList[E]` 
po stronie błędów.

Rozważmy wykonanie walidacji danych pochodzących od użytkownika za pomocą `Disjunction` i `flatMap`:

{lang="text"}
~~~~~~~~
  scala> :paste
         final case class Credentials(user: Username, name: Fullname)
         final case class Username(value: String) extends AnyVal
         final case class Fullname(value: String) extends AnyVal
  
         def username(in: String): String \/ Username =
           if (in.isEmpty) "empty username".left
           else if (in.contains(" ")) "username contains spaces".left
           else Username(in).right
  
         def realname(in: String): String \/ Fullname =
           if (in.isEmpty) "empty real name".left
           else Fullname(in).right
  
  scala> for {
           u <- username("sam halliday")
           r <- realname("")
         } yield Credentials(u, r)
  res = -\/(username contains spaces)
~~~~~~~~

Jeśli użyjemy `|@|`

{lang="text"}
~~~~~~~~
  scala> (username("sam halliday") |@| realname("")) (Credentials.apply)
  res = -\/(username contains spaces)
~~~~~~~~

nadal dostaniemy tylko pierwszy błąd. Wynika to z faktu że `Disjunction` jest `Monad`ą a jego metody
`.applyX` muszą być spójne z `.flatMap` i nie mogą zakładać że operacje mogą być wykonywane poza kolejnością.
Porównajmy to z:

{lang="text"}
~~~~~~~~
  scala> :paste
         def username(in: String): ValidationNel[String, Username] =
           if (in.isEmpty) "empty username".failureNel
           else if (in.contains(" ")) "username contains spaces".failureNel
           else Username(in).success
  
         def realname(in: String): ValidationNel[String, Fullname] =
           if (in.isEmpty) "empty real name".failureNel
           else Fullname(in).success
  
  scala> (username("sam halliday") |@| realname("")) (Credentials.apply)
  res = Failure(NonEmpty[username contains spaces,empty real name])
~~~~~~~~

Tym razem dostaliśmy z powrotem wszystkie napotkane błędy!

`Validation` ma wiele metod analogicznych do tych w `Disjunction`, takich jak `.fold`, `.swap` i `+++`, plus
kilka ekstra:

{lang="text"}
~~~~~~~~
  sealed abstract class Validation[+E, +A] {
    def append[F >: E: Semigroup, B >: A: Semigroup](x: F \?/ B]): F \?/ B = ...
  
    def disjunction: (E \/ A) = ...
    ...
  }
~~~~~~~~

`.append` (z aliasem `+|+`) ma taką samą sygnaturę jak `+++` ale preferuje wariant `success`

-   `failure(v1) +|+ failure(v2)` zwraca `failure(v1 |+| v2)`
-   `failure(v1) +|+ success(v2)` zwraca `success(v2)`
-   `success(v1) +|+ failure(v2)` zwraca `success(v1)`
-   `success(v1) +|+ success(v2)` zwraca `success(v1 |+| v2)`

A> `+|+` to zdziwiony robot c3p0.

`.disjunction` konwertuje `Validated[A, B]` do `A \/ B`. Dysjunkcja ma lustrzane metody `.validation` i `.validationNel`,
pozwalając tym samym na łatwe przełączanie się miedzy sekwencyjnym i równoległym zbieraniem błędów.

`\/` i `Validation` są bardziej wydajnymi alternatywami dla wyjątków typu checked do walidacji wejścia, unikającymi
zbierania śladu stosu (_stacktrace_). Wymagają one też od użytkownika obsłużenia potencjalnych błędów, sprawiając tym samym,
że tworzone systemy są bardziej niezawodne.

A> Jedną z najwolniejszych operacji na JVMie jest tworzenie wyjątków. Wynika to z zasobów potrzebnych do stworzenia 
A> śladu stosu. Tradycyjne podejście używające wyjątków do walidacji wejścia i parsowania potrafi być tysiąckrotnie 
A> wolniejsze od funkcji używających `\/` lub `Validation`.
A>
A> Niektórzy twierdzą że przewidywalne wyjątki są referencyjnie transparentne, ponieważ zostaną rzucone za każdym razem.
A> Jednak ślad stosu zależy od ciągu wywołań, dając inny rezultat zależnie od tego kto zawołał daną funkcje, zaburzając
A> tym samym transparencję referencyjną.
A> Nie mniej, rzucanie wyjątku nie jest czyste, ponieważ oznacza, że funkcja nie jest *totalna*.


### These

Napotkaliśmy `These`, strukturę danych wyrażającą logiczne LUB, kiedy poznawaliśmy `Align`.

{lang="text"}
~~~~~~~~
  sealed abstract class \&/[+A, +B] { ... }
  object \&/ {
    type These[A, B] = A \&/ B
  
    final case class This[A](aa: A) extends (A \&/ Nothing)
    final case class That[B](bb: B) extends (Nothing \&/ B)
    final case class Both[A, B](aa: A, bb: B) extends (A \&/ B)
  
    def apply[A, B](a: A, b: B): These[A, B] = Both(a, b)
  }
~~~~~~~~

z metodami upraszczającymi konstrukcję

{lang="text"}
~~~~~~~~
  implicit class TheseOps[A](self: A) {
    final def wrapThis[B]: A \&/ B = \&/.This(self)
    final def wrapThat[B]: B \&/ A = \&/.That(self)
  }
  implicit class ThesePairOps[A, B](self: (A, B)) {
    final def both: A \&/ B = \&/.Both(self._1, self._2)
  }
~~~~~~~~

`These` ma instancje typeklas

-   `Monad`
-   `Bitraverse`
-   `Traverse`
-   `Cobind`

oraz zależnie od zawartości

-   `Semigroup` / `Monoid` / `Band`
-   `Equal` / `Order`
-   `Show`

`These` (`\&/`) ma też wiele metod, których oczekiwalibyśmy od `Disjunction` (`\/`) i `Validation` (`\?/`)

{lang="text"}
~~~~~~~~
  sealed abstract class \&/[+A, +B] {
    def fold[X](s: A => X, t: B => X, q: (A, B) => X): X = ...
    def swap: (B \&/ A) = ...
  
    def append[X >: A: Semigroup, Y >: B: Semigroup](o: =>(X \&/ Y)): X \&/ Y = ...
  
    def &&&[X >: A: Semigroup, C](t: X \&/ C): X \&/ (B, C) = ...
    ...
  }
~~~~~~~~

`.append` ma 9 możliwych ułożeń i dane nigdy nie są tracone, ponieważ `This` i `That` mogą być zawsze
zamienione w `Both`.

`.flatMap` jest prawostronna (`Both` i `That`), przyjmując `Semigroup`y dla strony lewej (`This`), tak aby
móc połączyć zawartości zamiast je porzucać. Metoda `&&&` jest pomocna, gdy chcemy połączyć dwie instancje 
`\&/`, tworząc tuple z prawej strony i porzucając tę stronę zupełnie jeśli nie jest wypełniona w obu instancjach.

Mimo że zwracanie typu `\&/` z naszych funkcji jest kuszące, to jego nadużywanie to antywzorzec.
Głównym powodem dla używania `\&/` jest łączenie i dzielenie potencjalnie nieskończonych *strumieni* danych w 
skończonej pamięci. Dlatego też dostajemy do dyspozycji kilka przydatnych funkcji do operowania na `EphemeralStream`
(zaliasowanym tutaj aby zmieścić się w jednej linii) lub czymkolwiek z instancją `MonadPlus`

{lang="text"}
~~~~~~~~
  type EStream[A] = EphemeralStream[A]
  
  object \&/ {
    def concatThisStream[A, B](x: EStream[A \&/ B]): EStream[A] = ...
    def concatThis[F[_]: MonadPlus, A, B](x: F[A \&/ B]): F[A] = ...
  
    def concatThatStream[A, B](x: EStream[A \&/ B]): EStream[B] = ...
    def concatThat[F[_]: MonadPlus, A, B](x: F[A \&/ B]): F[B] = ...
  
    def unalignStream[A, B](x: EStream[A \&/ B]): (EStream[A], EStream[B]) = ...
    def unalign[F[_]: MonadPlus, A, B](x: F[A \&/ B]): (F[A], F[B]) = ...
  
    def merge[A: Semigroup](t: A \&/ A): A = ...
    ...
  }
~~~~~~~~


### Either Wyższego Rodzaju

Typ danych `Coproduct` (nie mylić z bardziej ogólnym pojęciem *koproduktu* w ADT) opakowuje `Disjunction`
dla konstruktorów typu:

{lang="text"}
~~~~~~~~
  final case class Coproduct[F[_], G[_], A](run: F[A] \/ G[A]) { ... }
  object Coproduct {
    def leftc[F[_], G[_], A](x: F[A]): Coproduct[F, G, A] = Coproduct(-\/(x))
    def rightc[F[_], G[_], A](x: G[A]): Coproduct[F, G, A] = Coproduct(\/-(x))
    ...
  }
~~~~~~~~

Instancje typeklas po prostu delegują do instancji zdefiniowanych dla `F[_]` i `G[_]`.

Najpopularniejszym przypadkiem, w którym zastosowanie znajduje `Coproduct`, to sytuacja gdy chcemy
stworzyć anonimowy koprodukt wielu ADT.


### Nie Tak Szybko

Wbudowane w Scalę tuple oraz podstawowe typy danych takie jak `Maybe` lub `Disjunction` są ewaluowane zachłannie 
(_eagerly-evaluated_).

Dla wygody zdefiniowane zostały warianty leniwe, mające instancje oczekiwanych typeklas:

{lang="text"}
~~~~~~~~
  sealed abstract class LazyTuple2[A, B] {
    def _1: A
    def _2: B
  }
  ...
  sealed abstract class LazyTuple4[A, B, C, D] {
    def _1: A
    def _2: B
    def _3: C
    def _4: D
  }
  
  sealed abstract class LazyOption[+A] { ... }
  private final case class LazySome[A](a: () => A) extends LazyOption[A]
  private case object LazyNone extends LazyOption[Nothing]
  
  sealed abstract class LazyEither[+A, +B] { ... }
  private case class LazyLeft[A, B](a: () => A) extends LazyEither[A, B]
  private case class LazyRight[A, B](b: () => B) extends LazyEither[A, B]
~~~~~~~~

Wnikliwy czytelnik zauważy, że przedrostek `Lazy` jest nie do końca poprawny, a nazwy tych typów danych 
prawdopodobnie powinny brzmieć: `ByNameTupleX`, `ByNameOption` i `ByNameEither`.


### Const

`Const`, zawdzięczający nazwę angielskiemu *constant*, czyli *stała*, jest opakowaniem na wartość typu `A`, razem
z nieużywanym parametrem typu `B`.

{lang="text"}
~~~~~~~~
  final case class Const[A, B](getConst: A)
~~~~~~~~

`Const` dostarcza instancję `Applicative[Const[A, ?]]` jeśli tylko dostępny jest `Monoid[A]`:

{lang="text"}
~~~~~~~~
  implicit def applicative[A: Monoid]: Applicative[Const[A, ?]] =
    new Applicative[Const[A, ?]] {
      def point[B](b: =>B): Const[A, B] =
        Const(Monoid[A].zero)
      def ap[B, C](fa: =>Const[A, B])(fbc: =>Const[A, B => C]): Const[A, C] =
        Const(fbc.getConst |+| fa.getConst)
    }
~~~~~~~~

Najważniejszą własnością tej instancji jest to, że ignoruje parametr `B`, łącząc wartości typu `A`, które napotka.

Wracając do naszej aplikacji `drone-dynamic-agents`, powinniśmy najpierw zrefaktorować plik `logic.scala` tak aby używał
`Applicative` zamiast `Monad`. Poprzednią implementację stworzyliśmy zanim jeszcze dowiedzieliśmy się czym jest
`Applicative`. Teraz wiemy jak zrobić to lepiej:


{lang="text"}
~~~~~~~~
  final class DynAgentsModule[F[_]: Applicative](D: Drone[F], M: Machines[F])
    extends DynAgents[F] {
    ...
    def act(world: WorldView): F[WorldView] = world match {
      case NeedsAgent(node) =>
        M.start(node) >| world.copy(pending = Map(node -> world.time))
  
      case Stale(nodes) =>
        nodes.traverse { node =>
          M.stop(node) >| node
        }.map { stopped =>
          val updates = stopped.strengthR(world.time).toList.toMap
          world.copy(pending = world.pending ++ updates)
        }
  
      case _ => world.pure[F]
    }
    ...
  }
~~~~~~~~

Skoro nasza logika biznesowa wymaga teraz jedynie `Applicative`, możemy zaimplementować nasz mock `F[a]` jako 
`Const[String, a]`. W każdym z przypadków zwracamy nazwę funkcji która została wywołana:

{lang="text"}
~~~~~~~~
  object ConstImpl {
    type F[a] = Const[String, a]
  
    private val D = new Drone[F] {
      def getBacklog: F[Int] = Const("backlog")
      def getAgents: F[Int]  = Const("agents")
    }
  
    private val M = new Machines[F] {
      def getAlive: F[Map[MachineNode, Epoch]]     = Const("alive")
      def getManaged: F[NonEmptyList[MachineNode]] = Const("managed")
      def getTime: F[Epoch]                        = Const("time")
      def start(node: MachineNode): F[Unit]        = Const("start")
      def stop(node: MachineNode): F[Unit]         = Const("stop")
    }
  
    val program = new DynAgentsModule[F](D, M)
  }
~~~~~~~~

Z taką interpretacją naszego programu możemy zweryfikować metody, które są używane:

{lang="text"}
~~~~~~~~
  it should "call the expected methods" in {
    import ConstImpl._
  
    val alive    = Map(node1 -> time1, node2 -> time1)
    val world    = WorldView(1, 1, managed, alive, Map.empty, time4)
  
    program.act(world).getConst shouldBe "stopstop"
  }
~~~~~~~~

Alternatywnie, moglibyśmy zliczyć ilość wywołań za pomocą `Const[Int, ?]` lub `IMap[String, Int]`.

W tym teście zrobiliśmy krok dalej poza tradycyjne testowanie z użyciem *Mocków*. `Const` pozwolił nam
sprawdzić co zostało wywołane bez dostarczania faktycznej implementacji. Podejście takie jest użyteczne
kiedy specyfikacja wymaga od nas abyśmy wykonali konkretne wywołania w odpowiedzi na dane wejście. 
Dodatkowo, osiągnęliśmy to zachowując bezpieczeństwo w czasie kompilacji.

Idąc dalej tym tokiem myślenia, powiedzmy że chcielibyśmy monitorować (w środowisku produkcyjnym) węzły,
które zatrzymywane są w metodzie `act`. Możemy stworzyć implementacje `Drone` i `Machines` używając `Const` i 
zawołać je z naszej opakowanej wersji `act`

{lang="text"}
~~~~~~~~
  final class Monitored[U[_]: Functor](program: DynAgents[U]) {
    type F[a] = Const[Set[MachineNode], a]
    private val D = new Drone[F] {
      def getBacklog: F[Int] = Const(Set.empty)
      def getAgents: F[Int]  = Const(Set.empty)
    }
    private val M = new Machines[F] {
      def getAlive: F[Map[MachineNode, Epoch]]     = Const(Set.empty)
      def getManaged: F[NonEmptyList[MachineNode]] = Const(Set.empty)
      def getTime: F[Epoch]                        = Const(Set.empty)
      def start(node: MachineNode): F[Unit]        = Const(Set.empty)
      def stop(node: MachineNode): F[Unit]         = Const(Set(node))
    }
    val monitor = new DynAgentsModule[F](D, M)
  
    def act(world: WorldView): U[(WorldView, Set[MachineNode])] = {
      val stopped = monitor.act(world).getConst
      program.act(world).strengthR(stopped)
    }
  }
~~~~~~~~

Możemy to zrobić, ponieważ `monitor` jest *czysty* i uruchomienie go nie produkuje żadnych efektów ubocznych.

Poniższy fragment uruchamia program z `ConstImpl`, ekstrahując wszystkie wywołania do `Machines.stop` i zwracając
wszystkie zatrzymane węzły razem `WoldView`

{lang="text"}
~~~~~~~~
  it should "monitor stopped nodes" in {
    val underlying = new Mutable(needsAgents).program
  
    val alive = Map(node1 -> time1, node2 -> time1)
    val world = WorldView(1, 1, managed, alive, Map.empty, time4)
    val expected = world.copy(pending = Map(node1 -> time4, node2 -> time4))
  
    val monitored = new Monitored(underlying)
    monitored.act(world) shouldBe (expected -> Set(node1, node2))
  }
~~~~~~~~

Użyliśmy `Const` aby zrobić coś co przypomina niegdyś popularne w Javie *Programowanie Aspektowe*. 
Na bazie naszej logiki biznesowej zaimplementowaliśmy monitoring nie komplikując tej logiki w żaden sposób.

A będzie jeszcze lepiej. Moglibyśmy uruchomić `ConstImpl` w środowisku produkcyjnym aby zebrać informacje
o tym co ma zostać zatrzymane, a następnie dostarczyć **zoptymalizowaną** implementację korzystającą
ze specyficznych dla implementacji wywołań batchowych.

Cichym bohaterem tej opowieści jest `Applicative`, a `Const` pozwala nam pokazać co jest dzięki niemu możliwe.
Jeśli musielibyśmy zmienić nasz program tak aby wymagał `Monad`y, nie moglibyśmy wtedy użyć `Const`, a zamiast tego zmuszeni 
bylibyśmy do napisania pełnoprawnych mocków aby zweryfikować jakie funkcje zostały wywołane dla danych argumentów.
*Reguła Najmniejsze Mocy* (_Rule of Least Power_) wymaga od nas abyśmy używali `Applicative` zamiast `Monad` kiedy tylko możemy.


## Kolekcje

W przeciwieństwie do Collections API z biblioteki standardowej, Scalaz opisuje zachowanie kolekcji
za pomocą hierarchii typeklas, np. `Foldable`, `Traverse`, `Monoid`. Co pozostaje do przeanalizowania, to 
konkretne struktury danych, ich charakterystyki wydajnościowe i wyspecjalizowane metody.

Ten podrozdział wnika w szczegóły implementacyjne każdego typu danych. Nie musimy zapamiętać wszystkiego, celem 
jest zrozumieć jak działa każda ze struktur jedynie w ogólności.

Ponieważ wszystkie kolekcje dostarczają instancje mniej więcej tych samych typeklas, nie będziemy ich powtarzać.
W większości przypadków jest to pewna wariacja poniższej listy.

-   `Monoid`
-   `Traverse` / `Foldable`
-   `MonadPlus` / `IsEmpty`
-   `Cobind` / `Comonad`
-   `Zip` / `Unzip`
-   `Align`
-   `Equal` / `Order`
-   `Show`

Struktury danych, które nigdy nie są puste dostarczają 

-   `Traverse1` / `Foldable1`

oraz `Semigroup` zamiast `Monoid` i `Plus` zamiast `IsEmpty`.


### Listy

Używaliśmy `IList[A]` i `NonEmptyList[A]` tyle razy że powinny już być nam znajome. 
Reprezentują on ideę klasycznej, jedno-połączeniowej listy:

{lang="text"}
~~~~~~~~
  sealed abstract class IList[A] {
    def ::(a: A): IList[A] = ...
    def :::(as: IList[A]): IList[A] = ...
    def toList: List[A] = ...
    def toNel: Option[NonEmptyList[A]] = ...
    ...
  }
  final case class INil[A]() extends IList[A]
  final case class ICons[A](head: A, tail: IList[A]) extends IList[A]
  
  final case class NonEmptyList[A](head: A, tail: IList[A]) {
    def <::(b: A): NonEmptyList[A] = nel(b, head :: tail)
    def <:::(bs: IList[A]): NonEmptyList[A] = ...
    ...
  }
~~~~~~~~

A> Kod źródłowy Scalaz 7.3 ujawnia, że `INil` jest zaimplementowany jako
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   sealed abstract case class INil[A] private() extends IList[A]
A>   object INil {
A>     private[this] val value: INil[Nothing] = new INil[Nothing]{}
A>     def apply[A](): IList[A] = value.asInstanceOf[IList[A]]
A>   }
A> ~~~~~~~~
A> 
A> wykorzystując detale implementacyjne JVMa aby uniknąć alokacji tworząc `INil`.
A> 
A> Taka optymalizacja jest ręcznie aplikowana do wszystkich bezparametrowych klas.
A> W praktyce Scalaz pełna jest podobnych optymalizacji, ale wszystkie są wcześniej
A> omawiane i wprowadzane tylko w oparciu o dowody na istotny wzrost wydajności przy pełnym zachowaniu
A> semantyki.

Główną zaletą `IList` nad `List` jest brak niebezpiecznych metod, takich jak `.head` (jest ona niebezpieczna, gdyż wyrzuca wyjątek 
w przypadku pustej kolekcji).

Dodatkowo, `IList` jest **dużo** prostsza, gdyż nie jest częścią hierarchii, oraz zużywa zdecydowanie mniej 
pamięci. Ponadto, `List` z biblioteki standardowej ma przerażająca implementację, używającą `var` aby obejść
problemy wydajnościowe:

{lang="text"}
~~~~~~~~
  package scala.collection.immutable
  
  sealed abstract class List[+A]
    extends AbstractSeq[A]
    with LinearSeq[A]
    with GenericTraversableTemplate[A, List]
    with LinearSeqOptimized[A, List[A]] { ... }
  case object Nil extends List[Nothing] { ... }
  final case class ::[B](
    override val head: B,
    private[scala] var tl: List[B]
  ) extends List[B] { ... }
~~~~~~~~

Tworzenie instancji `List` wymaga ostrożnej, i powolnej, synchronizacji wątków aby zapewnić
bezpieczne publikowanie. `IList` nie ma żadnych tego typu wymagań, a więc może przegonić `List` pod względem wydajności.

A> Czy `NonEmptyList` to nie po prostu `ICons`? Tak, ale tylko na poziomie struktur danych.
A> Różnica polega na tym, że `ICons` jest częścią ADT `IList`, a `NonEmptyList` nie. Instancje typeklas powinny
A> być zawsze definiowane na poziomie ADT, a nie pojedynczych wariantów, aby uniknąć zbędnej złożoności.


### `EphemeralStream`

`Stream` z biblioteki standardowej jest leniwą wersją `List`y, ale obarczoną wyciekami pamięci i niebezpiecznymi
metodami. `EphemeralStream` nie przetrzymuje referencji do wyliczonych wartości, łagodząc problemy z przetrzymywaniem
pamięci. Jednocześnie pozbawiony jest niebezpiecznych metod, tak jak `Ilist`.

{lang="text"}
~~~~~~~~
  sealed abstract class EphemeralStream[A] {
    def headOption: Option[A]
    def tailOption: Option[EphemeralStream[A]]
    ...
  }
  // private implementations
  object EphemeralStream extends EphemeralStreamInstances {
    type EStream[A] = EphemeralStream[A]
  
    def emptyEphemeralStream[A]: EStream[A] = ...
    def cons[A](a: =>A, as: =>EStream[A]): EStream[A] = ...
    def unfold[A, B](start: =>B)(f: B => Option[(A, B)]): EStream[A] = ...
    def iterate[A](start: A)(f: A => A): EStream[A] = ...
  
    implicit class ConsWrap[A](e: =>EStream[A]) {
      def ##::(h: A): EStream[A] = cons(h, e)
    }
    object ##:: {
      def unapply[A](xs: EStream[A]): Option[(A, EStream[A])] =
        if (xs.isEmpty) None
        else Some((xs.head(), xs.tail()))
    }
    ...
  }
~~~~~~~~

A> Używanie słowa *strumień* (_stream_) dla struktur danych podobnej natury powoli staje się przestarzałe. *Strumienie*
A> są teraz używane wraz z ✨ *Reactive Manifesto* ✨ przez działy marketingu oraz we frameworkach, takich jak Akka Streams.

`.cons`, `.unfold` i `.iterate` to mechanizmy do tworzenia strumieni. `##::` (a więc i `cons`) umieszcza nowy element na 
początku `EStream`u przekazanego przez nazwę. `.unfold` służy do tworzenia skończonych (lecz potencjalnie nieskończonych)
strumieni poprzez ciągłe wywoływanie funkcji `f` zwracającej następną wartość oraz wejście do swojego kolejnego wywołania.
`.iterate` tworzy nieskończony strumień za pomocą funkcji `f` wywoływanej na poprzednim jego elemencie.

`EStream` może pojawiać się w wyrażeniach pattern matchingu z użyciem symbolu `##::`.

A> `##::` wygląda trochę jak Exogorth: gigantyczny kosmiczny robak żyjący na asteroidach.

Mimo że `EStream` rozwiązuje problem przetrzymywania pamięci, nadal możemy ucierpieć z powodu 
*powolnych wycieków pamięci*, jeśli żywa referencja wskazuje na czoło nieskończonego strumienia. 
Problemy tej natury, oraz potrzeba komponowania strumieni wywołujących efekty uboczne są powodem, dla którego
istnieje biblioteka fs2.


### `CorecursiveList`

*Korekursja* (_Corecursion_) ma miejsce gdy zaczynamy ze stanu bazowego i deterministycznie produkujemy kolejne stany
przejściowe, tak jak miało to miejsce w metodzie `EphemeralStream.unfold`, którą niedawno omawialiśmy:

{lang="text"}
~~~~~~~~
  def unfold[A, B](b: =>B)(f: B => Option[(A, B)]): EStream[A] = ...
~~~~~~~~

Jest to działanie odwrotne do *rekursji*, która rozbija dane do stanu bazowego i kończy działanie.

`CorecursiveList` to struktura danych wyrażająca `EphemeralStream.unfold` i będąca alternatywą dla `EStream`, która
może być wydajniejsza w niektórych przypadkach:

{lang="text"}
~~~~~~~~
  sealed abstract class CorecursiveList[A] {
    type S
    def init: S
    def step: S => Maybe[(S, A)]
  }
  
  object CorecursiveList {
    private final case class CorecursiveListImpl[S0, A](
      init: S0,
      step: S0 => Maybe[(S0, A)]
    ) extends CorecursiveList[A] { type S = S0 }
  
    def apply[S, A](init: S)(step: S => Maybe[(S, A)]): CorecursiveList[A] =
      CorecursiveListImpl(init, step)
  
    ...
  }
~~~~~~~~

Korekursja jest przydatna gdy implementujemy `Comonad.cojoin`, jak w naszym przykładzie z `Hood`.
`CorecursiveList` to dobry sposób na wyrażenie nieliniowych równań rekurencyjnych, jak te używane w 
biologicznych modelach populacji, systemach kontroli, makroekonomii i modelach bankowości inwestycyjnej.


### `ImmutableArray`

Czyli proste opakowanie na mutowalną tablicę (`Array`) z biblioteki standardowej, ze specjalizacją
dla typów prymitywnych:

{lang="text"}
~~~~~~~~
  sealed abstract class ImmutableArray[+A] {
    def ++[B >: A: ClassTag](o: ImmutableArray[B]): ImmutableArray[B]
    ...
  }
  object ImmutableArray {
    final class StringArray(s: String) extends ImmutableArray[Char] { ... }
    sealed class ImmutableArray1[+A](as: Array[A]) extends ImmutableArray[A] { ... }
    final class ofRef[A <: AnyRef](as: Array[A]) extends ImmutableArray1[A](as)
    ...
    final class ofLong(as: Array[Long]) extends ImmutableArray1[Long](as)
  
    def fromArray[A](x: Array[A]): ImmutableArray[A] = ...
    def fromString(str: String): ImmutableArray[Char] = ...
    ...
  }
~~~~~~~~

Typ `Array` jest bezkonkurencyjny jeśli chodzi prędkość odczytu oraz wielkość stosu. Jednak
nie występuje tutaj w ogóle współdzielenie strukturalne, więc niemutowalne tablice używane są zwykle tylko gdy
ich zawartość nie ulega zmianie lub jako sposób na bezpieczne owinięcie danych pochodzących z zastanych
części systemu.


### `Dequeue`

`Dequeue` (wymawiana jak talia kart - "deck") to połączona lista, która pozwala na dodawanie i odczytywanie 
elementów z przodu (`cons`) lub tyłu (`snoc`) w stałym czasie. Usuwania elementów z obu końców jest 
stałe statystycznie.

{lang="text"}
~~~~~~~~
  sealed abstract class Dequeue[A] {
    def frontMaybe: Maybe[A]
    def backMaybe: Maybe[A]
  
    def ++(o: Dequeue[A]): Dequeue[A] = ...
    def +:(a: A): Dequeue[A] = cons(a)
    def :+(a: A): Dequeue[A] = snoc(a)
    def cons(a: A): Dequeue[A] = ...
    def snoc(a: A): Dequeue[A] = ...
    def uncons: Maybe[(A, Dequeue[A])] = ...
    def unsnoc: Maybe[(A, Dequeue[A])] = ...
    ...
  }
  private final case class SingletonDequeue[A](single: A) extends Dequeue[A] { ... }
  private final case class FullDequeue[A](
    front: NonEmptyList[A],
    fsize: Int,
    back: NonEmptyList[A],
    backSize: Int) extends Dequeue[A] { ... }
  private final case object EmptyDequeue extends Dequeue[Nothing] { ... }
  
  object Dequeue {
    def empty[A]: Dequeue[A] = EmptyDequeue()
    def apply[A](as: A*): Dequeue[A] = ...
    def fromFoldable[F[_]: Foldable, A](fa: F[A]): Dequeue[A] = ...
    ...
  }
~~~~~~~~

Implementacja bazuje na dwóch listach, jednej dla danych początkowych, drugiej dla końcowych.
Rozważmy instancję przechowującą symbole `a0, a1, a2, a3, a4, a5, a6`

{lang="text"}
~~~~~~~~
  FullDequeue(
    NonEmptyList('a0, IList('a1, 'a2, 'a3)), 4,
    NonEmptyList('a6, IList('a5, 'a4)), 3)
~~~~~~~~

która może być zobrazowana jako

{width=30%}
![](images/dequeue.png)

Zauważ, że lista przechowująca `back` jest w odwróconej kolejności.

Odczyt `snoc` (element końcowy) to proste spojrzenie na `back.head`. Dodanie elementu na koniec `Dequeue` oznacza
dodanie go na początek `back` i stworzenie nowego `FullDequeue` (co zwiększy `backSize` o jeden). Prawie 
cała oryginalna struktura jest współdzielona. Porównaj to z dodaniem nowego elementu na koniec `IList`, co wymaga
stworzenia na nowo całej struktury.

`frontSize` i `backSize` są używane do zbalansowywania `front` i `back`, tak, aby zawsze były podobnych rozmiarów.
Balansowanie oznacza, że niektóre operacje mogą być wolniejsze od innych (np. gdy cała struktura musi być przebudowana),
ale ponieważ dzieje się to okazjonalnie możemy ten koszt uśrednić i powiedzieć, że jest stały.


### `DList`

Zwykłe listy mają kiepską wydajność gdy duże listy są ze sobą łączone. Rozważmy koszt wykonania poniższej operacji:

{lang="text"}
~~~~~~~~
  ((as ::: bs) ::: (cs ::: ds)) ::: (es ::: (fs ::: gs))
~~~~~~~~

{width=50%}
![](images/dlist-list-append.png)

Tworzonych jest 6 list pośrednich, przechodząc i przebudowując każdą z list trzy krotnie (oprócz `gs`, która jest współdzielona
na wszystkich etapach).

`DList` (od *difference list*, listy różnic) jest bardziej wydajnym rozwiązaniem dla tego scenariusza. Zamiast 
wykonywać obliczenia na każdym z etapów wynik reprezentowany jest jako `IList[A] => IList[A]`.

{lang="text"}
~~~~~~~~
  final case class DList[A](f: IList[A] => IList[A]) {
    def toIList: IList[A] = f(IList.empty)
    def ++(as: DList[A]): DList[A] = DList(xs => f(as.f(xs)))
    ...
  }
  object DList {
    def fromIList[A](as: IList[A]): DList[A] = DList(xs => as ::: xs)
  }
~~~~~~~~

A> Jest to implementacja uproszczona, zawierająca błąd powodujący przepełnienie stosu, którym zajmiemy się
A> w rozdziale poświęconym Zaawansowanym Monadom.

Odpowiednikiem naszych obliczeń jest (symbole muszą zostać stworzone za pomocą `DList.fromIList`)

{lang="text"}
~~~~~~~~
  (((a ++ b) ++ (c ++ d)) ++ (e ++ (f ++ g))).toIList
~~~~~~~~

gdzie praca podzielona jest na *prawostronne* (czyli szybkie) złączenia

{lang="text"}
~~~~~~~~
  (as ::: (bs ::: (cs ::: (ds ::: (es ::: (fs ::: gs))))))
~~~~~~~~

wykorzystując szybki konstruktor na `IList`.

Jak zawsze, nie ma nic za darmo. Występuje tu narzut związany z alokacją pamięci, który może spowolnić
nasz kod, jeśli ten i tak zakładał prawostronne złączenia. Największe przyspieszenie uzyskamy gdy operacje są lewostronne, np.: 

{lang="text"}
~~~~~~~~
  ((((((as ::: bs) ::: cs) ::: ds) ::: es) ::: fs) ::: gs)
~~~~~~~~

Lista różnic cierpi z powodu kiepskiego marketingu. Najprawdopodobniej znalazłaby się w bibliotece standardowej, gdyby 
tylko nazywała się `ListBuilderFactory`.


### `ISet`

Struktury drzewiaste są doskonałe do przechowywania uporządkowanych danych, tak aby każdy *węzeł binarny*
przechowywał elementy od niego *mniejsze* w jednej gałęzi, a *większe* w drugiej. Jednak naiwna implementacja
takie struktury może w łatwy sposób stać się niesymetryczna, zależnie od kolejności dodawanie elementów. Możliwym jest
utrzymywanie perfekcyjnie zbalansowanego drzewa, ale jest to niewiarygodnie nieefektywne, ponieważ każde wstawienie
elementu do drzewa powodowałoby jego pełne przebudowanie.

`ISet` to implementacja drzewa z *ograniczoną równowagą* (_bounded balance_), co oznacza, że jest ono zrównoważone
w przybliżeniu, używając `size` każdej gałęzi do równoważenia węzła.

{lang="text"}
~~~~~~~~
  sealed abstract class ISet[A] {
    val size: Int = this match {
      case Tip()        => 0
      case Bin(_, l, r) => 1 + l.size + r.size
    }
    ...
  }
  object ISet {
    private final case class Tip[A]() extends ISet[A]
    private final case class Bin[A](a: A, l: ISet[A], r: ISet[A]) extends ISet[A]
  
    def empty[A]: ISet[A] = Tip()
    def singleton[A](x: A): ISet[A] = Bin(x, Tip(), Tip())
    def fromFoldable[F[_]: Foldable, A: Order](xs: F[A]): ISet[A] =
      xs.foldLeft(empty[A])((a, b) => a insert b)
    ...
  }
~~~~~~~~

`ISet` wymaga aby `A` miało instancję typeklasy `Order` oraz musi ona pozostawać taka sama pomiędzy wywołaniami, 
gdyż inaczej zaburzone zostaną wewnętrzne założenia, prowadząc tym samym do uszkodzenia danych. Innymi słowy, 
zakładamy spójność typeklas, a więc dla dowolnego `A` istnieje tylko jedna instancja `Order[A]`.

ADT `ISet`u niestety pozwala na wyrażenie niepoprawnych drzew. Staramy się pisać ADT tak, aby
w pełni opisywały co jest i nie jest możliwe poprzez restrykcję typów, ale nie zawsze jest to możliwe.
Zamiast tego `Tip` i `Bin`  są prywatne, powstrzymując użytkowników przed przypadkowym niepoprawnych drzew.
`.insert` jest jedynym sposobem na konstrukcję drzew, definiując tym samym jak wygląda poprawna jego forma.

{lang="text"}
~~~~~~~~
  sealed abstract class ISet[A] {
    ...
    def contains(x: A)(implicit o: Order[A]): Boolean = ...
    def union(other: ISet[A])(implicit o: Order[A]): ISet[A] = ...
    def delete(x: A)(implicit o: Order[A]): ISet[A] = ...
  
    def insert(x: A)(implicit o: Order[A]): ISet[A] = this match {
      case Tip() => ISet.singleton(x)
      case self @ Bin(y, l, r) => o.order(x, y) match {
        case LT => balanceL(y, l.insert(x), r)
        case GT => balanceR(y, l, r.insert(x))
        case EQ => self
      }
    }
    ...
  }
~~~~~~~~

Wewnętrzne metody `.balanceL` i `.balanceR` są swoimi lustrzanymi odbiciami, a więc przestudiujemy jedynie
`.balanceL`, która jest wywoływana gdy dodawana wartość jest *mniejsza* niż aktualny węzeł. Jest ona również
wołana przez metodę `.delete`.

{lang="text"}
~~~~~~~~
  def balanceL[A](y: A, left: ISet[A], right: ISet[A]): ISet[A] = (left, right) match {
  ...
~~~~~~~~

Równoważenie wymaga abyśmy sklasyfikowali scenariusze, które mogą się zdarzyć. Przejdziemy przez nie kolejno, 
wizualizując `(y, left, right)` po lewej stronie i wersją zbalansowaną (znaną tez jako *drzewo obrócone*, 
_rotated tree_) po prawej.

-   wypełnione koła obrazują `Tip`
-   trzy kolumny to `left | value | right` pochodzące z `Bin`
-   diamenty wizualizują dowolny `ISet`

Pierwszy scenariusz jest trywialny i zachodzi gdy obie strony to `Tip`y. Nigdy nie napotkamy tego scenariusza wykonując
`.insert` ale może on wystąpić przy `.delete`

{lang="text"}
~~~~~~~~
  case (Tip(), Tip()) => singleton(y)
~~~~~~~~

{width=50%}
![](images/balanceL-1.png)

Drugi przypadek ma miejsce kiedy lewa strona to `Bin` zawierający jedynie `Tip`. Nie musimy nic równoważyć, dodajemy 
jedynie oczywiste połączenie:

{lang="text"}
~~~~~~~~
  case (Bin(lx, Tip(), Tip()), Tip()) => Bin(y, left, Tip())
~~~~~~~~

{width=60%}
![](images/balanceL-2.png)

Przy trzecim scenariuszu zaczyna robić się interesująco: lewa strona to `Bin` zawierający
`Bin` po swojej prawej stronie.

{lang="text"}
~~~~~~~~
  case (Bin(lx, Tip(), Bin(lrx, _, _)), Tip()) =>
    Bin(lrx, singleton(lx), singleton(y))
~~~~~~~~

{width=70%}
![](images/balanceL-3.png)

Ale co z dwoma diamentami poniżej `lrx`? Czy nie utraciliśmy właśnie informacji? Nie, nie utraciliśmy, ponieważ
możemy wnioskować (na podstawie równoważenia wielkości), że są one zawsze puste (`Tip`). Nie istnieje żadna reguła
w naszych scenariuszach, która pozwala na wyprodukowanie drzewa, w którym którykolwiek z tych węzłów to `Bin`.

Czwarty przypadek jest przeciwieństwem trzeciego.

{lang="text"}
~~~~~~~~
  case (Bin(lx, ll, Tip()), Tip()) => Bin(lx, ll, singleton(y))
~~~~~~~~

{width=70%}
![](images/balanceL-4.png)

W scenariuszu piątym mamy do czynienia z pełnymi drzewami po obu stronach `left` i musimy oprzeć
decyzję o dalszych krokach na ich wielkości.

{lang="text"}
~~~~~~~~
  case (Bin(lx, ll, lr), Tip()) if (2*ll.size > lr.size) =>
    Bin(lx, ll, Bin(y, lr, Tip()))
  case (Bin(lx, ll, Bin(lrx, lrl, lrr)), Tip()) =>
    Bin(lrx, Bin(lx, ll, lrl), Bin(y, lrr, Tip()))
~~~~~~~~

Dla pierwszej gałęzi, `2*ll.size > lr.size`

{width=50%}
![](images/balanceL-5a.png)

a dla drugiej, `2*ll.size <= lr.size`

{width=75%}
![](images/balanceL-5b.png)

Szósty przypadek wprowadza drzewo po prawej stronie. Gdy `left` jest puste tworzymy oczywiste połączenie. 
Taka sytuacja nigdy nie pojawia się w wyniku `.insert` ponieważ `left` jest zawsze pełne:

{lang="text"}
~~~~~~~~
  case (Tip(), r) => Bin(y, Tip(), r)
~~~~~~~~

{width=50%}
![](images/balanceL-6.png)

Ostatni scenariusz zachodzi gdy mamy pełne drzewa po obu stronach. Jeśli `left` jest mniejszy niż 
trzykrotność `right`, możemy po prostu stworzyć nowy `Bin`.

{lang="text"}
~~~~~~~~
  case _ if l.size <= 3 * r.size => Bin(y, l, r)
~~~~~~~~

{width=50%}
![](images/balanceL-7a.png)

Jednak gdy ten warunek nie jest spełniony i `left` jest większy od `right` więcej niż trzykrotnie, musimy
zrównoważyć drzewa jak w przypadku piątym.

{lang="text"}
~~~~~~~~
  case (Bin(lx, ll, lr), r) if (2*ll.size > lr.size) =>
    Bin(lx, ll, Bin(y, lr, r))
  case (Bin(lx, ll, Bin(lrx, lrl, lrr)), r) =>
    Bin(lrx, Bin(lx, ll, lrl), Bin(y, lrr, r))
~~~~~~~~

{width=60%}
![](images/balanceL-7b.png)

{width=75%}
![](images/balanceL-7c.png)

Tym samym doszliśmy do końca analizy metody `.insert` i tego jak tworzony jest `ISet`. Nie powinno dziwić, że
`Foldable` jest zaimplementowany w oparciu o przeszukiwanie-w-głąb. Metody takie jak `.minimum` i `.maximum`
są optymalne, gdyż struktura danych bazuje na uporządkowaniu elementów.

Warto zaznaczyć, że niektóre metody *nie mogą* być zaimplementowane tak wydajnie jak byśmy chcieli. Rozważmy
sygnaturę `Foldable.element`

{lang="text"}
~~~~~~~~
  @typeclass trait Foldable[F[_]] {
    ...
    def element[A: Equal](fa: F[A], a: A): Boolean
    ...
  }
~~~~~~~~

Oczywistą implementacją `.element` jest użyć przeszukiwania (prawie) binarnego `ISet.contains`.
Jednak nie jest to możliwe, gdyż `.element` dostarcza `Equal`, a `.contains` wymaga instancji `Order`.

Z tego samego powodu `ISet` nie jest w stanie dostarczyć instancji typeklasy `Functor`, co w praktyce okazuje się
sensownym ograniczeniem: wykonanie `.map` powodowałoby przebudowanie całej struktury. Rozsądnie jest przekonwertować
nasz zbiór do innego typu danych, na przykład `IList`, wykonać `.map` i przekonwertować wynik z powrotem. W konsekwencji
nie jesteśmy w stanie uzyskać `Traverse[ISet]` ani `Applicative[ISet]`.


### `IMap`

{lang="text"}
~~~~~~~~
  sealed abstract class ==>>[A, B] {
    val size: Int = this match {
      case Tip()           => 0
      case Bin(_, _, l, r) => 1 + l.size + r.size
    }
  }
  object ==>> {
    type IMap[A, B] = A ==>> B
  
    private final case class Tip[A, B]() extends (A ==>> B)
    private final case class Bin[A, B](
      key: A,
      value: B,
      left: A ==>> B,
      right: A ==>> B
    ) extends ==>>[A, B]
  
    def apply[A: Order, B](x: (A, B)*): A ==>> B = ...
  
    def empty[A, B]: A ==>> B = Tip[A, B]()
    def singleton[A, B](k: A, x: B): A ==>> B = Bin(k, x, Tip(), Tip())
    def fromFoldable[F[_]: Foldable, A: Order, B](fa: F[(A, B)]): A ==>> B = ...
    ...
  }
~~~~~~~~

Wygląda znajomo? W rzeczy samej, `IMap` (alias na operator prędkości światła `==>>`) to kolejne równoważone
drzewo, z tą różnicą, że każdy węzeł zawiera dodatkowe pole `value: B`, pozwalając na przechowywanie par klucz/wartość.
Instancja `Order` wymagana jest jedynie dla typu klucza `A`, a dodatkowo dostajemy zestaw przydatnych metod do aktualizowania 
wpisów

{lang="text"}
~~~~~~~~
  sealed abstract class ==>>[A, B] {
    ...
    def adjust(k: A, f: B => B)(implicit o: Order[A]): A ==>> B = ...
    def adjustWithKey(k: A, f: (A, B) => B)(implicit o: Order[A]): A ==>> B = ...
    ...
  }
~~~~~~~~


### `StrictTree` i `Tree`

Zarówno `StrictTree` jak i `Tree` to implementacje *Rose Tree*, drzewiastej struktury danych z nieograniczoną
ilością gałęzi w każdym węźle. Niestety, z powodów historycznych, zbudowane na bazie kolekcji z biblioteki standardowej...

{lang="text"}
~~~~~~~~
  case class StrictTree[A](
    rootLabel: A,
    subForest: Vector[StrictTree[A]]
  )
~~~~~~~~

`Tree` to leniwa (_by-need_) wersja `StrictTree` z wygodnymi konstruktorami

{lang="text"}
~~~~~~~~
  class Tree[A](
    rootc: Need[A],
    forestc: Need[Stream[Tree[A]]]
  ) {
    def rootLabel = rootc.value
    def subForest = forestc.value
  }
  object Tree {
    object Node {
      def apply[A](root: =>A, forest: =>Stream[Tree[A]]): Tree[A] = ...
    }
    object Leaf {
      def apply[A](root: =>A): Tree[A] = ...
    }
  }
~~~~~~~~

Użytkownik Rose Tree powinien sam zadbać o balansowanie drzewa, co jest odpowiednie gdy chcemy
wyrazić hierarchiczną wiedzę domenową jako strukturę danych. Dla przykładu, w sztucznej inteligencji
Rose Tree może być użyte w [algorytmach klastrowania](https://arxiv.org/abs/1203.3468)
do organizacji danych w hierarchie coraz bardziej podobnych rzeczy. Możliwe jest również wyrażenie dokumentów XML
jako Rose Tree.

Pracując z danymi hierarchicznymi dobrze jest rozważyć tę strukturę danych zanim stworzymy swoją własną.

### `FingerTree`

Finger tree (drzewo-dłoń, drzewo palczaste?) to uogólniona sekwencja z zamortyzowanym stałym czasem dostępu do elementów i logarytmicznym
złączaniem. `A` to typ elementów, a `V` na razie zignorujemy:

{lang="text"}
~~~~~~~~
  sealed abstract class FingerTree[V, A] {
    def +:(a: A): FingerTree[V, A] = ...
    def :+(a: =>A): FingerTree[V, A] = ...
    def <++>(right: =>FingerTree[V, A]): FingerTree[V, A] = ...
    ...
  }
  object FingerTree {
    private class Empty[V, A]() extends FingerTree[V, A]
    private class Single[V, A](v: V, a: =>A) extends FingerTree[V, A]
    private class Deep[V, A](
      v: V,
      left: Finger[V, A],
      spine: =>FingerTree[V, Node[V, A]],
      right: Finger[V, A]
    ) extends FingerTree[V, A]
  
    sealed abstract class Finger[V, A]
    final case class One[V, A](v: V, a1: A) extends Finger[V, A]
    final case class Two[V, A](v: V, a1: A, a2: A) extends Finger[V, A]
    final case class Three[V, A](v: V, a1: A, a2: A, a3: A) extends Finger[V, A]
    final case class Four[V, A](v: V, a1: A, a2: A, a3: A, a4: A) extends Finger[V, A]
  
    sealed abstract class Node[V, A]
    private class Node2[V, A](v: V, a1: =>A, a2: =>A) extends Node[V, A]
    private class Node3[V, A](v: V, a1: =>A, a2: =>A, a3: =>A) extends Node[V, A]
    ...
  }
~~~~~~~~

A> `<++>` to bombowiec TIE, ale przyznajemy, że wysyłanie torped protonowych to lekka przesada.
A> Jego działanie jest takie same jak `|+|` czyli `Monoid`owego Myśliwca TIE.

Przedstawmy `FingerTree` jako kropki, `Finger` jako prostokąty, a `Node` jako prostokąty wewnątrz prostokątów:

{width=35%}
![](images/fingertree.png)

Dodanie elementy na początek `FingerTree` za pomocą `+:` jest wydajne, ponieważ `Deep` po prostu dodaje nowy element
do swojego lewego (`left`) palca. Jeśli palec to `Four`, to przebudowujemy `spine`, tak, aby przyjął 3 z tych 
elementów jako `Node3`. Dodawanie na koniec (`:+`) odbywa się tak samo, ale w odwrotnej kolejności.

Złączanie za pomocą `|+|` lub `<++>` jest bardziej wydajne niż dodawanie po jednym elemencie, ponieważ instancje `Deep` 
mogą zachować swoje zewnętrzne gałęzie przebudowując jedynie `spine`.

Do tej pory ignorowaliśmy `V`. Ukryliśmy też niejawny parametr obecny we wszystkich wariantach tego ADT: `implicit measurer: Reducer[A, V]`.

A> Przechowywanie instancji typeklas w ADT jest w kiepskim stylu, a dodatkowo zwiększa ilość wymaganej pamięci o 
A> 64 bity na każdy element. Implementacja `FingerTree` ma niemal dekadę na karku i nadaje się do przepisania.

`Reducer` to rozszerzenie typeklasy `Monoid` pozwalające na dodanie pojedynczych elementów do `M`

{lang="text"}
~~~~~~~~
  class Reducer[C, M: Monoid] {
    def unit(c: C): M
  
    def snoc(m: M, c: C): M = append(m, unit(c))
    def cons(c: C, m: M): M = append(unit(c), m)
  }
~~~~~~~~

Na przykład `Reducer[A, IList[A]]` może zapewnić wydajną implementację `.cons`

{lang="text"}
~~~~~~~~
  implicit def reducer[A]: Reducer[A, IList[A]] = new Reducer[A, IList[A]] {
    override def unit(a: A): IList[A] = IList.single(a)
    override def cons(a: A, as: IList[A]): IList[A] = a :: as
  }
~~~~~~~~

A> `Reducer` powinien nazywać się `CanActuallyBuildFrom` na cześć podobnie nazywającej się klasy z biblioteki standardowej,
A> ponieważ w praktyce służy on do budowania kolekcji.


#### `IndSeq`

Jeśli jako `V` użyjemy `Int`, dostaniemy sekwencje zindeksowaną, gdzie miarą jest *wielkość*, pozwalając nam
na wyszukiwanie elementu po indeksie poprzez porównywanie indeksu z rozmiarem każdej gałezi w drzewie:

{lang="text"}
~~~~~~~~
  final class IndSeq[A](val self: FingerTree[Int, A])
  object IndSeq {
    private implicit def sizer[A]: Reducer[A, Int] = _ => 1
    def apply[A](as: A*): IndSeq[A] = ...
  }
~~~~~~~~

#### `OrdSeq`

Inną odmianą `FingerTree` jest sekwencja uporządkowana, gdzie miarą jest największa wartość w danej gałęzi:

{lang="text"}
~~~~~~~~
  final class OrdSeq[A: Order](val self: FingerTree[LastOption[A], A]) {
    def partition(a: A): (OrdSeq[A], OrdSeq[A]) = ...
    def insert(a: A): OrdSeq[A] = ...
    def ++(xs: OrdSeq[A]): OrdSeq[A] = ...
  }
  object OrdSeq {
    private implicit def keyer[A]: Reducer[A, LastOption[A]] = a => Tag(Some(a))
    def apply[A: Order](as: A*): OrdSeq[A] = ...
  }
~~~~~~~~

`OrdSeq` nie posiada instancji żadnych typeklas, co sprawia, że przydatna jest tylko do stopniowego budowania
uporządkowanej sekwencji (zawierającej duplikaty). Jeśli zajdzie taka potrzeba, możemy zawsze skorzystać z bazowego `FingerTree`.


#### `Cord`

Najpopularniejszym użyciem `FingerTree` jest przechowanie tymczasowej reprezentacji `String`ów w instancjach `Show`.
Budowanie pojedynczego `String`a może być tysiąckrotnie szybsze niż domyślna implementacja `.toString` dla `case class`, 
która tworzy nowy ciąg znaków dla każdej warstwy w ADT.

{lang="text"}
~~~~~~~~
  final case class Cord(self: FingerTree[Int, String]) {
    override def toString: String = {
      val sb = new java.lang.StringBuilder(self.measure)
      self.foreach(sb.append) // locally scoped side effect
      sb.toString
    }
    ...
  }
~~~~~~~~

Dla przykładu, instancja `Cord[String]` zwraca `Three` ze stringiem po środku i cudzysłowami po obu stronach

{lang="text"}
~~~~~~~~
  implicit val show: Show[String] = s => Cord(FingerTree.Three("\"", s, "\""))
~~~~~~~~

Sprawiając że `String` wygląda tak jak w kodzie źródłowym

{lang="text"}
~~~~~~~~
  scala> val s = "foo"
         s.toString
  res: String = foo
  
  scala> s.show
  res: Cord = "foo"
~~~~~~~~

A> `Cord` w Scalaz 7.2 nie jest niestety tak szybki jak mógłby być. W Scalaz 7.3 zostało to poprawione
A> za pomocą [specjalnej struktury danych zoptymalizowanej do złączania `String`ów](https://github.com/scalaz/scalaz/pull/1793).


### Kolejka Priorytetowa `Heap`

*Kolejka priorytetowa* to struktura danych, która pozwala na szybkie wstawianie uporządkowanych elementów
(zezwalając na duplikaty) oraz szybki dostęp do *najmniejszego* elementu (czyli takiego z najwyższym priorytetem).
Nie jest wymagane, aby elementy inne niż minimalny były przechowywane wg. porządku. Naiwna implementacja mogłaby
wyglądać tak:

{lang="text"}
~~~~~~~~
  final case class Vip[A] private (val peek: Maybe[A], xs: IList[A]) {
    def push(a: A)(implicit O: Order[A]): Vip[A] = peek match {
      case Maybe.Just(min) if a < min => Vip(a.just, min :: xs)
      case _                          => Vip(peek, a :: xs)
    }
  
    def pop(implicit O: Order[A]): Maybe[(A, Vip[A])] = peek strengthR reorder
    private def reorder(implicit O: Order[A]): Vip[A] = xs.sorted match {
      case INil()           => Vip(Maybe.empty, IList.empty)
      case ICons(min, rest) => Vip(min.just, rest)
    }
  }
  object Vip {
    def fromList[A: Order](xs: IList[A]): Vip[A] = Vip(Maybe.empty, xs).reorder
  }
~~~~~~~~

Taki `push` jest bardzo szybki (`O(1)`), ale `reorder`, a zatem i `pop` bazują na metodzie `IList.sorted`, której
złożoność to `O(n log n)`.

Scalaz implementuje kolejkę priorytetową za pomocą struktury drzewiastej, gdzie każdy węzeł ma wartość
mniejszą niż jego dzieci. `Heap` pozwala na szybkie wstawianie (`insert`), złączanie (`union`), sprawdzanie 
wielkości (`size`), zdejmowanie (`pop`) i podglądanie (`minimum0`) najmniejszego elementu.

{lang="text"}
~~~~~~~~
  sealed abstract class Heap[A] {
    def insert(a: A)(implicit O: Order[A]): Heap[A] = ...
    def +(a: A)(implicit O: Order[A]): Heap[A] = insert(a)
  
    def union(as: Heap[A])(implicit O: Order[A]): Heap[A] = ...
  
    def uncons(implicit O: Order[A]): Option[(A, Heap[A])] = minimumO strengthR deleteMin
    def minimumO: Option[A] = ...
    def deleteMin(implicit O: Order[A]): Heap[A] = ...
  
    ...
  }
  object Heap {
    def fromData[F[_]: Foldable, A: Order](as: F[A]): Heap[A] = ...
  
    private final case class Ranked[A](rank: Int, value: A)
  
    private final case class Empty[A]() extends Heap[A]
    private final case class NonEmpty[A](
      size: Int,
      tree: Tree[Ranked[A]]
    ) extends Heap[A]
  
    ...
  }
~~~~~~~~

A> wartość `size` jest memoizowana wewnątrz ADT aby pozwolić na natychmiastowe wykonanie `Foldable.length`,
A> w zamian za dodatkowe 64 bity pamięci za każdy element. Moglibyśmy zaimplementować wariant `Heap` z 
A> mniejszym zużyciem pamięci i wolniejszym `Foldable.length`.

`Heap` zaimplementowany jest za pomogą Rose `Tree` z wartościami typu `Ranked`, gdzie `rank` to głębokość
subdrzewa, pozwalająca na balansowanie całej struktury. Samodzielnie zarządzamy drzewem, tak aby `minimum` 
było zawsze na jego szczycie. Zaletą takiej reprezentacji jest to, że `minimum0` jest darmowe:

{lang="text"}
~~~~~~~~
  def minimumO: Option[A] = this match {
    case Empty()                        => None
    case NonEmpty(_, Tree.Node(min, _)) => Some(min.value)
  }
~~~~~~~~

Dodając nowy element porównujemy ją z aktualnym minimum i podmieniamy je jeśli nowa wartość jest mniejsza:

{lang="text"}
~~~~~~~~
  def insert(a: A)(implicit O: Order[A]): Heap[A] = this match {
    case Empty() =>
      NonEmpty(1, Tree.Leaf(Ranked(0, a)))
    case NonEmpty(size, tree @ Tree.Node(min, _)) if a <= min.value =>
      NonEmpty(size + 1, Tree.Node(Ranked(0, a), Stream(tree)))
  ...
~~~~~~~~

Dodawanie wartości, które nie są minimum skutkuje *nieuporządkowanymi* gałęziami drzewa. Kiedy napotkamy
dwa, lub więcej, poddrzewa tej samej rangi, optymistycznie dodajemy minimum na początek:

{lang="text"}
~~~~~~~~
  ...
    case NonEmpty(size, Tree.Node(min,
           (t1 @ Tree.Node(Ranked(r1, x1), xs1)) #::
           (t2 @ Tree.Node(Ranked(r2, x2), xs2)) #:: ts)) if r1 == r2 =>
      lazy val t0 = Tree.Leaf(Ranked(0, a))
      val sub =
        if (x1 <= a && x1 <= x2)
          Tree.Node(Ranked(r1 + 1, x1), t0 #:: t2 #:: xs1)
        else if (x2 <= a && x2 <= x1)
          Tree.Node(Ranked(r2 + 1, x2), t0 #:: t1 #:: xs2)
        else
          Tree.Node(Ranked(r1 + 1, a), t1 #:: t2 #:: Stream())
  
      NonEmpty(size + 1, Tree.Node(Ranked(0, min.value), sub #:: ts))
  
    case NonEmpty(size,  Tree.Node(min, rest)) =>
      val t0 = Tree.Leaf(Ranked(0, a))
      NonEmpty(size + 1, Tree.Node(Ranked(0, min.value), t0 #:: rest))
  }
~~~~~~~~

Uniknięcie pełnego uporządkowania drzewa sprawia, że `insert` jest bardzo szybki (`O(1)`), a więc
producencie wartości nie są karani. Jednak konsumenci muszą ponieść koszt tej decyzji, gdyż
złożoność `uncons` to `O(log n)`, z racji tego, że musimy odszukać nowe minimum i przebudować drzewo.
Nadal jednak jest to implementacja szybsza od naiwnej.

`union` również odracza porządkowanie pozwalając na wykonanie ze złożonością `O(1)`.

Jeśli domyślna instancja `Order[Foo]` nie wyraża w poprawny sposób priorytetów, których potrzebujemy, możemy
użyć `Tag`u i dostarczyć własną instancję `Order[Foo @@ Custom]` dla `Heap[Foo @@ Custom]`.


### `Diev` (Interwały Dyskretne)

Możemy wydajnie wyrazić (nieuporządkowany) ciąg liczb całkowitych 6, 9, 2, 13, 8, 14, 10, 7, 5 jako serię domkniętych przedziałów:
`[2, 2], [5, 10], [13, 14]`. `Diev` to wydajna implementacja tej metody dla dowolnego `A`, dla którego istnieje 
`Enum[A]`. Tym wydajniejsza im gęstsza jego zawartość.

{lang="text"}
~~~~~~~~
  sealed abstract class Diev[A] {
    def +(interval: (A, A)): Diev[A]
    def +(value: A): Diev[A]
    def ++(other: Diev[A]): Diev[A]
  
    def -(interval: (A, A)): Diev[A]
    def -(value: A): Diev[A]
    def --(other: Diev[A]): Diev[A]
  
    def intervals: Vector[(A, A)]
    def contains(value: A): Boolean
    def contains(interval: (A, A)): Boolean
    ...
  }
  object Diev {
    private final case class DieVector[A: Enum](
      intervals: Vector[(A, A)]
    ) extends Diev[A]
  
    def empty[A: Enum]: Diev[A] = ...
    def fromValuesSeq[A: Enum](values: Seq[A]): Diev[A] = ...
    def fromIntervalsSeq[A: Enum](intervals: Seq[(A, A)]): Diev[A] = ...
  }
~~~~~~~~

Kiedy aktualizujemy `Diev` sąsiednie przedziały są łączone i porządkowane, tak, że dla każdego zbioru wartości
istnieje unikalna reprezentacja.

{lang="text"}
~~~~~~~~
  scala> Diev.fromValuesSeq(List(6, 9, 2, 13, 8, 14, 10, 7, 5))
  res: Diev[Int] = ((2,2)(5,10)(13,14))
  
  scala> Diev.fromValuesSeq(List(6, 9, 2, 13, 8, 14, 10, 7, 5).reverse)
  res: Diev[Int] = ((2,2)(5,10)(13,14))
~~~~~~~~

Świetnym zastosowaniem dla tej struktury są przedziały czasu. Na przykład w `TradeTemplate` z wcześniejszego rozdziału.

{lang="text"}
~~~~~~~~
  final case class TradeTemplate(
    payments: List[java.time.LocalDate],
    ccy: Option[Currency],
    otc: Option[Boolean]
  )
~~~~~~~~

Jeśli okaże się, że lista `payments` jest bardzo gęsta, możemy zamienić ją na `Diev` dla zwiększenia wydajności, 
nie wpływając jednocześnie na logikę biznesową, gdyż polega ona na typeklasie `Monoid`, a nie metodach specyficznych
dla `List`y. Musimy jednak dostarczyć instancję `Enum[LocalDate]`, co jest rzeczą całkiem przydatną.


### `OneAnd`

Przypomnij sobie `Foldable`, czyli odpowiednik API kolekcji ze Scalaz, oraz `Foldable1`, czyli jego wersję dla 
niepustych kolekcji. Do tej pory widzieliśmy instancję `Foldable1` jedynie dla `NonEmptyList`. Okazuje się, że 
`OneAnd` to prosta struktura danych, która potrafi opakować dowolną kolekcję i zamienić ją w `Foldable1`:

{lang="text"}
~~~~~~~~
  final case class OneAnd[F[_], A](head: A, tail: F[A])
~~~~~~~~

Typ `NonEmptyList[A]` mógłby być aliasem na `OneAnd[IList, A]`. W podobny sposób możemy stworzyć niepuste
wersje `Stream`, `DList` i `Tree`. Jednak może to zaburzyć gwarancje co do uporządkowania i unikalności elementów: 
`OneAnd[ISet, A]` to nie tyle niepusty `ISet`, a raczej `ISet` z zagwarantowanym pierwszym elementem, który może
również znajdować się w tym zbiorze.


## Podsumowanie

W tym rozdziale przejrzeliśmy typy danych, jakie Scalaz ma do zaoferowania.

Nie musimy zapamiętać wszystkiego. Niech każda z sekcji zasadzi ziarno pewnej koncepcji, które może o sobie przypomnieć
gdy będziemy szukać rozwiązania dla konkretnego problemu.

Świat funkcyjnych struktur danych jest aktywnie badany i rozwijany. Publikacje naukowe na ten temat ukazują się
regularnie, pokazując nowe podejścia do znanych problemów. Implementacja nowych struktur bazujących na literaturze 
to dobry sposób na swój własny wkład do ekosystemu Scalaz.


# Advanced Monads

You have to know things like Advanced Monads in order to be an advanced
functional programmer.

However, we are developers yearning for a simple life, and our idea of
"advanced" is modest. To put it into context: `scala.concurrent.Future` is more
complicated and nuanced than any `Monad` in this chapter.

In this chapter we will study some of the most important implementations of
`Monad`.


## Always in motion is the `Future`

The biggest problem with `Future` is that it eagerly schedules work during
construction. As we discovered in the introduction, `Future` conflates the
definition of a program with *interpreting* it (i.e. running it).

`Future` is also bad from a performance perspective: every time `.flatMap` is
called, a closure is submitted to an `Executor`, resulting in unnecessary thread
scheduling and context switching. It is not unusual to see 50% of our CPU power
dealing with thread scheduling, instead of doing the work. So much so that
parallelising work with `Future` can often make it *slower*.

Combined, eager evaluation and executor submission means that it is impossible
to know when a job started, when it finished, or the sub-tasks that were spawned
to calculate the final result. It should not surprise us that performance
monitoring "solutions" for `Future` based frameworks are a solid earner for the
modern day snake oil merchant.

Furthermore, `Future.flatMap` requires an `ExecutionContext` to be in implicit
scope: users are forced to think about business logic and execution semantics at
the same time.

A> If `Future` was a Star Wars character, it would be Anakin Skywalker: the fallen
A> chosen one, rushing in and breaking things without thinking.


## Effects and Side Effects

If we cannot call side-effecting methods in our business logic, or in `Future`
(or `Id`, or `Either`, or `Const`, etc), **when can** we write them? The answer
is: in a `Monad` that delays execution until it is interpreted at the
application's entrypoint. We can now refer to I/O and mutation as an *effect* on
the world, captured by the type system, as opposed to having a hidden
*side-effect*.

The simplest implementation of such a `Monad` is `IO`, formalising the version
we wrote in the introduction:

{lang="text"}
~~~~~~~~
  final class IO[A](val interpret: () => A)
  object IO {
    def apply[A](a: =>A): IO[A] = new IO(() => a)
  
    implicit val monad: Monad[IO] = new Monad[IO] {
      def point[A](a: =>A): IO[A] = IO(a)
      def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = IO(f(fa.interpret()).interpret())
    }
  }
~~~~~~~~

The `.interpret` method is only called once, in the entrypoint of an
application:

{lang="text"}
~~~~~~~~
  def main(args: Array[String]): Unit = program.interpret()
~~~~~~~~

However, there are two big problems with this simple `IO`:

1.  it can stack overflow
2.  it doesn't support parallel computations

Both of these problems will be overcome in this chapter. However, no matter how
complicated the internal implementation of a `Monad`, the principles described
here remain true: we're modularising the definition of a program and its
execution, such that we can capture effects in type signatures, allowing us to
reason about them, and reuse more code.

A> The Scala compiler will happily allow us to call side-effecting methods from
A> unsafe code blocks. The [Scalafix](https://scalacenter.github.io/scalafix/) linting tool can ban side-effecting methods at
A> compiletime, unless called from inside a deferred `Monad` like `IO`.


## Stack Safety

On the JVM, every method call adds an entry to the call stack of the `Thread`,
like adding to the front of a `List`. When the method completes, the method at
the `head` is thrown away. The maximum length of the call stack is determined by
the `-Xss` flag when starting up `java`. Tail recursive methods are detected by
the Scala compiler and do not add an entry. If we hit the limit, by calling too
many chained methods, we get a `StackOverflowError`.

Unfortunately, every nested call to our `IO`'s `.flatMap` adds another method
call to the stack. The easiest way to see this is to repeat an action forever,
and see if it survives for longer than a few seconds. We can use `.forever`,
from `Apply` (a parent of `Monad`):

{lang="text"}
~~~~~~~~
  scala> val hello = IO { println("hello") }
  scala> Apply[IO].forever(hello).interpret()
  
  hello
  hello
  hello
  ...
  hello
  java.lang.StackOverflowError
      at java.io.FileOutputStream.write(FileOutputStream.java:326)
      at ...
      at monadio.IO$$anon$1.$anonfun$bind$1(monadio.scala:18)
      at monadio.IO$$anon$1.$anonfun$bind$1(monadio.scala:18)
      at ...
~~~~~~~~

Scalaz has a typeclass that `Monad` instances can implement if they are stack
safe: `BindRec` requires a constant stack space for recursive `bind`:

{lang="text"}
~~~~~~~~
  @typeclass trait BindRec[F[_]] extends Bind[F] {
    def tailrecM[A, B](f: A => F[A \/ B])(a: A): F[B]
  
    override def forever[A, B](fa: F[A]): F[B] = ...
  }
~~~~~~~~

We don't need `BindRec` for all programs, but it is essential for a general
purpose `Monad` implementation.

The way to achieve stack safety is to convert method calls into references to an
ADT, the `Free` monad:

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A]
  object Free {
    private final case class Return[S[_], A](a: A)     extends Free[S, A]
    private final case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
    private final case class Gosub[S[_], A0, B](
      a: Free[S, A0],
      f: A0 => Free[S, B]
    ) extends Free[S, B] { type A = A0 }
    ...
  }
~~~~~~~~

A> `SUSPEND`, `RETURN` and `GOSUB` are a tip of the hat to the `BASIC` commands of
A> the same name: pausing, completing, and continuing a subroutine, respectively.

The `Free` ADT is a natural data type representation of the `Monad` interface:

1.  `Return` represents `.point`
2.  `Gosub` represents `.bind` / `.flatMap`

When an ADT mirrors the arguments of related functions, it is called a *Church
encoding*.

`Free` is named because it can be *generated for free* for any `S[_]`. For
example, we could set `S` to be the `Drone` or `Machines` algebras from Chapter
3 and generate a data structure representation of our program. We will return to
why this is useful at the end of this chapter.


### `Trampoline`

`Free` is more general than we need for now. Setting the algebra `S[_]` to `()
=> ?`, a deferred calculation or *thunk*, we get `Trampoline` and can implement
a stack safe `Monad`

{lang="text"}
~~~~~~~~
  object Free {
    type Trampoline[A] = Free[() => ?, A]
    implicit val trampoline: Monad[Trampoline] with BindRec[Trampoline] =
      new Monad[Trampoline] with BindRec[Trampoline] {
        def point[A](a: =>A): Trampoline[A] = Return(a)
        def bind[A, B](fa: Trampoline[A])(f: A => Trampoline[B]): Trampoline[B] =
          Gosub(fa, f)
  
        def tailrecM[A, B](f: A => Trampoline[A \/ B])(a: A): Trampoline[B] =
          bind(f(a)) {
            case -\/(a) => tailrecM(f)(a)
            case \/-(b) => point(b)
          }
      }
    ...
  }
~~~~~~~~

The `BindRec` implementation, `.tailrecM`, runs `.bind` until we get a `B`.
Although this is not technically a `@tailrec` implementation, it uses constant
stack space because each call returns a heap object, with delayed recursion.

A> Called `Trampoline` because every time we `.bind` on the stack, we *bounce* back
A> to the heap.
A> 
A> The only Star Wars reference involving bouncing is Yoda's duel with Dooku. We
A> shall not speak of this again.

Convenient functions are provided to create a `Trampoline` eagerly (`.done`) or
by-name (`.delay`). We can also create a `Trampoline` from a by-name
`Trampoline` (`.suspend`):

{lang="text"}
~~~~~~~~
  object Trampoline {
    def done[A](a: A): Trampoline[A]                  = Return(a)
    def delay[A](a: =>A): Trampoline[A]               = suspend(done(a))
    def suspend[A](a: =>Trampoline[A]): Trampoline[A] = unit >> a
  
    private val unit: Trampoline[Unit] = Suspend(() => done(()))
  }
~~~~~~~~

When we see `Trampoline[A]` in a codebase we can always mentally substitute it
with `A`, because it is simply adding stack safety to the pure computation. We
get the `A` by interpreting `Free`, provided by `.run`.

A> It is instructive, although not necessary, to understand how `Free.run` is
A> implemented: `.resume` evaluates a single layer of the `Free`, and `go` runs it
A> to completion.
A> 
A> In the following `Trampoline[A]` is used as a synonym for `Free[() => ?, A]` to
A> make the code easier to read.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   sealed abstract class Trampoline[A] {
A>     def run: A = go(f => f())
A>   
A>     def go(f: () => Trampoline[A] => Trampoline[A]): A = {
A>       @tailrec def go2(t: Trampoline[A]): A = t.resume match {
A>         case -\/(s) => go2(f(s))
A>         case \/-(r) => r
A>       }
A>       go2(this)
A>     }
A>   
A>     @tailrec def resume: () => Trampoline[A] \/ A = this match {
A>       case Return(a) => \/-(a)
A>       case Suspend(t) => -\/(t.map(Return(_)))
A>       case Gosub(Return(a), f) => f(a).resume
A>       case Gosub(Suspend(t), f) => -\/(t.map(f))
A>       case Gosub(Gosub(a, g), f) => a >>= (z => g(z) >>= f).resume
A>     }
A>     ...
A>   }
A> ~~~~~~~~
A> 
A> The case that is most likely to cause confusion is when we have nested `Gosub`:
A> apply the inner function `g` then pass it to the outer one `f`, it is just
A> function composition.


### Example: Stack Safe `DList`

In the previous chapter we described the data type `DList` as

{lang="text"}
~~~~~~~~
  final case class DList[A](f: IList[A] => IList[A]) {
    def toIList: IList[A] = f(IList.empty)
    def ++(as: DList[A]): DList[A] = DList(xs => f(as.f(xs)))
    ...
  }
~~~~~~~~

However, the actual implementation looks more like:

{lang="text"}
~~~~~~~~
  final case class DList[A](f: IList[A] => Trampoline[IList[A]]) {
    def toIList: IList[A] = f(IList.empty).run
    def ++(as: =>DList[A]): DList[A] = DList(xs => suspend(as.f(xs) >>= f))
    ...
  }
~~~~~~~~

Instead of applying nested calls to `f` we use a suspended `Trampoline`. We
interpret the trampoline with `.run` only when needed, e.g. in `toIList`. The
changes are minimal, but we now have a stack safe `DList` that can rearrange the
concatenation of a large number lists without blowing the stack!


### Stack Safe `IO`

Similarly, our `IO` can be made stack safe thanks to `Trampoline`:

{lang="text"}
~~~~~~~~
  final class IO[A](val tramp: Trampoline[A]) {
    def unsafePerformIO(): A = tramp.run
  }
  object IO {
    def apply[A](a: =>A): IO[A] = new IO(Trampoline.delay(a))
  
    implicit val Monad: Monad[IO] with BindRec[IO] =
      new Monad[IO] with BindRec[IO] {
        def point[A](a: =>A): IO[A] = IO(a)
        def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] =
          new IO(fa.tramp >>= (a => f(a).tramp))
        def tailrecM[A, B](f: A => IO[A \/ B])(a: A): IO[B] = ...
      }
  }
~~~~~~~~

A> We heard you like `Monad`, so we made you a `Monad` out of a `Monad`, so you can
A> monadically bind when you are monadically binding.

The interpreter, `.unsafePerformIO()`, has an intentionally scary name to
discourage using it except in the entrypoint of the application.

This time, we don't get a stack overflow error:

{lang="text"}
~~~~~~~~
  scala> val hello = IO { println("hello") }
  scala> Apply[IO].forever(hello).unsafePerformIO()
  
  hello
  hello
  hello
  ...
  hello
~~~~~~~~

Using a `Trampoline` typically introduces a performance regression vs a regular
reference. It is `Free` in the sense of *freely generated*, not *free as in
beer*.

A> Always benchmark instead of accepting sweeping statements about performance: it
A> may well be the case that the garbage collector performs better for an
A> application when using `Free` because of the reduced size of retained objects in
A> the stack.


## Monad Transformer Library

Monad transformers are data structures that wrap an underlying value and provide
a monadic *effect*.

For example, in Chapter 2 we used `OptionT` to let us use `F[Option[A]]` in a
`for` comprehension as if it was just a `F[A]`. This gave our program the effect
of an *optional* value. Alternatively, we can get the effect of optionality if
we have a `MonadPlus`.

This subset of data types and extensions to `Monad` are often referred to as the
*Monad Transformer Library* (MTL), summarised below. In this section, we will
explain each of the transformers, why they are useful, and how they work.

| Effect               | Underlying            | Transformer | Typeclass     |
|-------------------- |--------------------- |----------- |------------- |
| optionality          | `F[Maybe[A]]`         | `MaybeT`    | `MonadPlus`   |
| errors               | `F[E \/ A]`           | `EitherT`   | `MonadError`  |
| a runtime value      | `A => F[B]`           | `ReaderT`   | `MonadReader` |
| journal / multitask  | `F[(W, A)]`           | `WriterT`   | `MonadTell`   |
| evolving state       | `S => F[(S, A)]`      | `StateT`    | `MonadState`  |
| keep calm & carry on | `F[E \&/ A]`          | `TheseT`    |               |
| control flow         | `(A => F[B]) => F[B]` | `ContT`     |               |


### `MonadTrans`

Each transformer has the general shape `T[F[_], A]`, providing at least an
instance of `Monad` and `Hoist` (and therefore `MonadTrans`):

{lang="text"}
~~~~~~~~
  @typeclass trait MonadTrans[T[_[_], _]] {
    def liftM[F[_]: Monad, A](a: F[A]): T[F, A]
  }
  
  @typeclass trait Hoist[F[_[_], _]] extends MonadTrans[F] {
    def hoist[M[_]: Monad, N[_]](f: M ~> N): F[M, ?] ~> F[N, ?]
  }
~~~~~~~~

A> `T[_[_], _]` is another example of a higher kinded type. It says that `T` takes
A> two type parameters: the first also takes a type parameter, written `_[_]`, and
A> the second does not take any type parameters, written `_`.

`.liftM` lets us create a monad transformer if we have an `F[A]`. For example,
we can create an `OptionT[IO, String]` by calling `.liftM[OptionT]` on an
`IO[String]`.

`.hoist` is the same idea, but for natural transformations.

Generally, there are three ways to create a monad transformer:

-   from the underlying, using the transformer's constructor
-   from a single value `A`, using `.pure` from the `Monad` syntax
-   from an `F[A]`, using `.liftM` from the `MonadTrans` syntax

Due to the way that type inference works in Scala, this often means that a
complex type parameter must be explicitly written. As a workaround, transformers
provide convenient constructors on their companion that are easier to use.


### `MaybeT`

`OptionT`, `MaybeT` and `LazyOptionT` have similar implementations, providing
optionality through `Option`, `Maybe` and `LazyOption`, respectively. We will
focus on `MaybeT` to avoid repetition.

{lang="text"}
~~~~~~~~
  final case class MaybeT[F[_], A](run: F[Maybe[A]])
  object MaybeT {
    def just[F[_]: Applicative, A](v: =>A): MaybeT[F, A] =
      MaybeT(Maybe.just(v).pure[F])
    def empty[F[_]: Applicative, A]: MaybeT[F, A] =
      MaybeT(Maybe.empty.pure[F])
    ...
  }
~~~~~~~~

providing a `MonadPlus`

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad] = new MonadPlus[MaybeT[F, ?]] {
    def point[A](a: =>A): MaybeT[F, A] = MaybeT.just(a)
    def bind[A, B](fa: MaybeT[F, A])(f: A => MaybeT[F, B]): MaybeT[F, B] =
      MaybeT(fa.run >>= (_.cata(f(_).run, Maybe.empty.pure[F])))
  
    def empty[A]: MaybeT[F, A] = MaybeT.empty
    def plus[A](a: MaybeT[F, A], b: =>MaybeT[F, A]): MaybeT[F, A] = a orElse b
  }
~~~~~~~~

This monad looks fiddly, but it is just delegating everything to the `Monad[F]`
and then re-wrapping with a `MaybeT`. It is plumbing.

With this monad we can write logic that handles optionality in the `F[_]`
context, rather than carrying around `Option` or `Maybe`.

For example, say we are interfacing with a social media website to count the
number of stars a user has, and we start with a `String` that may or may not
correspond to a user. We have this algebra:

{lang="text"}
~~~~~~~~
  trait Twitter[F[_]] {
    def getUser(name: String): F[Maybe[User]]
    def getStars(user: User): F[Int]
  }
  def T[F[_]](implicit t: Twitter[F]): Twitter[F] = t
~~~~~~~~

We need to call `getUser` followed by `getStars`. If we use `Monad` as our
context, our function is difficult because we have to handle the `Empty` case:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): F[Maybe[Int]] = for {
    maybeUser  <- T.getUser(name)
    maybeStars <- maybeUser.traverse(T.getStars)
  } yield maybeStars
~~~~~~~~

However, if we have a `MonadPlus` as our context, we can suck `Maybe` into the
`F[_]` with `.orEmpty`, and forget about it:

{lang="text"}
~~~~~~~~
  def stars[F[_]: MonadPlus: Twitter](name: String): F[Int] = for {
    user  <- T.getUser(name) >>= (_.orEmpty[F])
    stars <- T.getStars(user)
  } yield stars
~~~~~~~~

However adding a `MonadPlus` requirement can cause problems downstream if the
context does not have one. The solution is to either change the context of the
program to `MaybeT[F, ?]` (lifting the `Monad[F]` into a `MonadPlus`), or to
explicitly use `MaybeT` in the return type, at the cost of slightly more code:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): MaybeT[F, Int] = for {
    user  <- MaybeT(T.getUser(name))
    stars <- T.getStars(user).liftM[MaybeT]
  } yield stars
~~~~~~~~

The decision to require a more powerful `Monad` vs returning a transformer is
something that each team can decide for themselves based on the interpreters
that they plan on using for their program.


### `EitherT`

An optional value is a special case of a value that may be an error, but we
don't know anything about the error. `EitherT` (and the lazy variant
`LazyEitherT`) allows us to use any type we want as the error value, providing
contextual information about what went wrong.

`EitherT` is a wrapper around an `F[A \/ B]`

{lang="text"}
~~~~~~~~
  final case class EitherT[F[_], A, B](run: F[A \/ B])
  object EitherT {
    def either[F[_]: Applicative, A, B](d: A \/ B): EitherT[F, A, B] = ...
    def leftT[F[_]: Functor, A, B](fa: F[A]): EitherT[F, A, B] = ...
    def rightT[F[_]: Functor, A, B](fb: F[B]): EitherT[F, A, B] = ...
    def pureLeft[F[_]: Applicative, A, B](a: A): EitherT[F, A, B] = ...
    def pure[F[_]: Applicative, A, B](b: B): EitherT[F, A, B] = ...
    ...
  }
~~~~~~~~

The `Monad` is a `MonadError`

{lang="text"}
~~~~~~~~
  @typeclass trait MonadError[F[_], E] extends Monad[F] {
    def raiseError[A](e: E): F[A]
    def handleError[A](fa: F[A])(f: E => F[A]): F[A]
  }
~~~~~~~~

`.raiseError` and `.handleError` are self-descriptive: the equivalent of `throw`
and `catch` an exception, respectively.

`MonadError` has some addition syntax for dealing with common problems:

{lang="text"}
~~~~~~~~
  implicit final class MonadErrorOps[F[_], E, A](self: F[A])(implicit val F: MonadError[F, E]) {
    def attempt: F[E \/ A] = ...
    def recover(f: E => A): F[A] = ...
    def emap[B](f: A => E \/ B): F[B] = ...
  }
~~~~~~~~

`.attempt` brings errors into the value, which is useful for exposing errors in
subsystems as first class values.

`.recover` is for turning an error into a value for all cases, as opposed to
`.handleError` which takes an `F[A]` and therefore allows partial recovery.

`.emap`, *either* map, is to apply transformations that can fail.

The `MonadError` for `EitherT` is:

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad, E] = new MonadError[EitherT[F, E, ?], E] {
    def monad[F[_]: Monad, E] = new MonadError[EitherT[F, E, ?], E] {
    def bind[A, B](fa: EitherT[F, E, A])
                  (f: A => EitherT[F, E, B]): EitherT[F, E, B] =
      EitherT(fa.run >>= (_.fold(_.left[B].pure[F], b => f(b).run)))
    def point[A](a: =>A): EitherT[F, E, A] = EitherT.pure(a)
  
    def raiseError[A](e: E): EitherT[F, E, A] = EitherT.pureLeft(e)
    def handleError[A](fa: EitherT[F, E, A])
                      (f: E => EitherT[F, E, A]): EitherT[F, E, A] =
      EitherT(fa.run >>= {
        case -\/(e) => f(e).run
        case right => right.pure[F]
      })
  }
~~~~~~~~

It should be of no surprise that we can rewrite the `MonadPlus` example with
`MonadError`, inserting informative error messages:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Twitter](name: String)
                          (implicit F: MonadError[F, String]): F[Int] = for {
    user  <- T.getUser(name) >>= (_.orError(s"user '$name' not found")(F))
    stars <- T.getStars(user)
  } yield stars
~~~~~~~~

where `.orError` is a convenience method on `Maybe`

{lang="text"}
~~~~~~~~
  sealed abstract class Maybe[A] {
    ...
    def orError[F[_], E](e: E)(implicit F: MonadError[F, E]): F[A] =
      cata(F.point(_), F.raiseError(e))
  }
~~~~~~~~

A> It is common to use `implicit` parameter blocks instead of context bounds when
A> the signature of the typeclass has more than one parameter.
A> 
A> It is also common practice to name the implicit parameter after the primary
A> type, in this case `F`.

The version using `EitherT` directly looks like

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): EitherT[F, String, Int] = for {
    user <- EitherT(T.getUser(name).map(_ \/> s"user '$name' not found"))
    stars <- EitherT.rightT(T.getStars(user))
  } yield stars
~~~~~~~~

The simplest instance of `MonadError` is for `\/`, perfect for testing business
logic that requires a `MonadError`. For example,

{lang="text"}
~~~~~~~~
  final class MockTwitter extends Twitter[String \/ ?] {
    def getUser(name: String): String \/ Maybe[User] =
      if (name.contains(" ")) Maybe.empty.right
      else if (name === "wobble") "connection error".left
      else User(name).just.right
  
    def getStars(user: User): String \/ Int =
      if (user.name.startsWith("w")) 10.right
      else "stars have been replaced by hearts".left
  }
~~~~~~~~

Our unit tests for `.stars` might cover these cases:

{lang="text"}
~~~~~~~~
  scala> stars("wibble")
  \/-(10)
  
  scala> stars("wobble")
  -\/(connection error)
  
  scala> stars("i'm a fish")
  -\/(user 'i'm a fish' not found)
  
  scala> stars("fommil")
  -\/(stars have been replaced by hearts)
~~~~~~~~

As we've now seen several times, we can focus on testing the pure business logic
without distraction.

Finally, if we return to our `JsonClient` algebra from Chapter 4.3

{lang="text"}
~~~~~~~~
  trait JsonClient[F[_]] {
    def get[A: JsDecoder](
      uri: String Refined Url,
      headers: IList[(String, String)]
    ): F[A]
    ...
  }
~~~~~~~~

recall that we only coded the happy path into the API. If our interpreter for
this algebra only works for an `F` having a `MonadError` we get to define the
kinds of errors as a tangential concern. Indeed, we can have **two** layers of
error if we define the interpreter for a `EitherT[IO, JsonClient.Error, ?]`

{lang="text"}
~~~~~~~~
  object JsonClient {
    sealed abstract class Error
    final case class ServerError(status: Int)       extends Error
    final case class DecodingError(message: String) extends Error
  }
~~~~~~~~

which cover I/O (network) problems, server status problems, and issues with our
modelling of the server's JSON payloads.


#### Choosing an error type

The community is undecided on the best strategy for the error type `E` in
`MonadError`.

One school of thought says that we should pick something general, like a
`String`. The other school says that an application should have an ADT of
errors, allowing different errors to be reported or handled differently. An
unprincipled gang prefers using `Throwable` for maximum JVM compatibility.

There are two problems with an ADT of errors on the application level:

-   it is very awkward to create a new error. One file becomes a monolithic
    repository of errors, aggregating the ADTs of individual subsystems.
-   no matter how granular the errors are, the resolution is often the same: log
    it and try it again, or give up. We don't need an ADT for this.

An error ADT is of value if every entry allows a different kind of recovery to
be performed.

A compromise between an error ADT and a `String` is an intermediary format. JSON
is a good choice as it can be understood by most logging and monitoring
frameworks.

A problem with not having a stacktrace is that it can be hard to localise which
piece of code was the source of an error. With [`sourcecode` by Li Haoyi](https://github.com/lihaoyi/sourcecode/), we can
include contextual information as metadata in our errors:

{lang="text"}
~~~~~~~~
  final case class Meta(fqn: String, file: String, line: Int)
  object Meta {
    implicit def gen(implicit fqn: sourcecode.FullName,
                              file: sourcecode.File,
                              line: sourcecode.Line): Meta =
      new Meta(fqn.value, file.value, line.value)
  }
  
  final case class Err(msg: String)(implicit val meta: Meta)
~~~~~~~~

Although `Err` is referentially transparent, the implicit construction of a
`Meta` does **not** appear to be referentially transparent from a natural reading:
two calls to `Meta.gen` (invoked implicitly when creating an `Err`) will produce
different values because the location in the source code impacts the returned
value:

{lang="text"}
~~~~~~~~
  scala> println(Err("hello world").meta)
  Meta(com.acme,<console>,10)
  
  scala> println(Err("hello world").meta)
  Meta(com.acme,<console>,11)
~~~~~~~~

To understand this, we have to appreciate that `sourcecode.*` methods are macros
that are generating source code for us. If we were to write the above explicitly
it is clear what is happening:

{lang="text"}
~~~~~~~~
  scala> println(Err("hello world")(Meta("com.acme", "<console>", 10)).meta)
  Meta(com.acme,<console>,10)
  
  scala> println(Err("hello world")(Meta("com.acme", "<console>", 11)).meta)
  Meta(com.acme,<console>,11)
~~~~~~~~

Yes, we've made a deal with the macro devil, but we could also write the `Meta`
manually and have it go out of date quicker than our documentation.


### `ReaderT`

The reader monad wraps `A => F[B]` allowing a program `F[B]` to depend on a
runtime value `A`. For those familiar with dependency injection, the reader
monad is the FP equivalent of Spring or Guice's `@Inject`, without the XML and
reflection.

`ReaderT` is just an alias to another more generally useful data type named
after the mathematician *Heinrich Kleisli*.

{lang="text"}
~~~~~~~~
  type ReaderT[F[_], A, B] = Kleisli[F, A, B]
  
  final case class Kleisli[F[_], A, B](run: A => F[B]) {
    def dimap[C, D](f: C => A, g: B => D)(implicit F: Functor[F]): Kleisli[F, C, D] =
      Kleisli(c => run(f(c)).map(g))
  
    def >=>[C](k: Kleisli[F, B, C])(implicit F: Bind[F]): Kleisli[F, A, C] = ...
    def >==>[C](k: B => F[C])(implicit F: Bind[F]): Kleisli[F, A, C] = this >=> Kleisli(k)
    ...
  }
  object Kleisli {
    implicit def kleisliFn[F[_], A, B](k: Kleisli[F, A, B]): A => F[B] = k.run
    ...
  }
~~~~~~~~

A> Some people call `>=>` the *fish operator*. There's always a bigger fish, hence
A> `>==>`. They are also called *Kleisli arrows*.

An `implicit` conversion on the companion allows us to use a `Kleisli` in place
of a function, so we can provide it as the parameter to a monad's `.bind`, or
`>>=`.

The most common use for `ReaderT` is to provide environment information to a
program. In `drone-dynamic-agents` we need access to the user's Oauth 2.0
Refresh Token to be able to contact Google. The obvious thing is to load the
`RefreshTokens` from disk on startup, and make every method take a
`RefreshToken` parameter. In fact, this is such a common requirement that Martin
Odersky has proposed [implicit functions](https://www.scala-lang.org/blog/2016/12/07/implicit-function-types.html).

A better solution is for our program to have an algebra that provides the
configuration when needed, e.g.

{lang="text"}
~~~~~~~~
  trait ConfigReader[F[_]] {
    def token: F[RefreshToken]
  }
~~~~~~~~

We have reinvented `MonadReader`, the typeclass associated to `ReaderT`, where
`.ask` is the same as our `.token`, and `S` is `RefreshToken`:

{lang="text"}
~~~~~~~~
  @typeclass trait MonadReader[F[_], S] extends Monad[F] {
    def ask: F[S]
  
    def local[A](f: S => S)(fa: F[A]): F[A]
  }
~~~~~~~~

with the implementation

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad, R] = new MonadReader[Kleisli[F, R, ?], R] {
    def point[A](a: =>A): Kleisli[F, R, A] = Kleisli(_ => F.point(a))
    def bind[A, B](fa: Kleisli[F, R, A])(f: A => Kleisli[F, R, B]) =
      Kleisli(a => Monad[F].bind(fa.run(a))(f))
  
    def ask: Kleisli[F, R, R] = Kleisli(_.pure[F])
    def local[A](f: R => R)(fa: Kleisli[F, R, A]): Kleisli[F, R, A] =
      Kleisli(f andThen fa.run)
  }
~~~~~~~~

A law of `MonadReader` is that the `S` cannot change between invocations, i.e.
`ask >> ask === ask`. For our usecase, this is to say that the configuration is
read once. If we decide later that we want to reload configuration every time we
need it, e.g. allowing us to change the token without restarting the
application, we can reintroduce `ConfigReader` which has no such law.

In our OAuth 2.0 implementation we could first move the `Monad` evidence onto the
methods:

{lang="text"}
~~~~~~~~
  def bearer(refresh: RefreshToken)(implicit F: Monad[F]): F[BearerToken] =
    for { ...
~~~~~~~~

and then refactor the `refresh` parameter to be part of the `Monad`

{lang="text"}
~~~~~~~~
  def bearer(implicit F: MonadReader[F, RefreshToken]): F[BearerToken] =
    for {
      refresh <- F.ask
~~~~~~~~

Any parameter can be moved into the `MonadReader`. This is of most value to
immediate callers when they simply want to thread through this information from
above. With `ReaderT`, we can reserve `implicit` parameter blocks entirely for
the use of typeclasses, reducing the mental burden of using Scala.

The other method in `MonadReader` is `.local`

{lang="text"}
~~~~~~~~
  def local[A](f: S => S)(fa: F[A]): F[A]
~~~~~~~~

We can change `S` and run a program `fa` within that local context, returning to
the original `S`. A use case for `.local` is to generate a "stack trace" that
makes sense to our domain. giving us nested logging! Leaning on the `Meta` data
structure from the previous section, we define a function to checkpoint:

{lang="text"}
~~~~~~~~
  def traced[A](fa: F[A])(implicit F: MonadReader[F, IList[Meta]]): F[A] =
    F.local(Meta.gen :: _)(fa)
~~~~~~~~

and we can use it to wrap functions that operate in this context.

{lang="text"}
~~~~~~~~
  def foo: F[Foo] = traced(getBar) >>= barToFoo
~~~~~~~~

automatically passing through anything that is not explicitly traced. A compiler
plugin or macro could do the opposite, opting everything in by default.

If we access `.ask` we can see the breadcrumb trail of exactly how we were
called, without the distraction of bytecode implementation details. A
referentially transparent stacktrace!

A defensive programmer may wish to truncate the `IList[Meta]` at a certain
length to avoid the equivalent of a stack overflow. Indeed, a more appropriate
data structure is `Dequeue`.

`.local` can also be used to keep track of contextual information that is
directly relevant to the task at hand, like the number of spaces that must
indent a line when pretty printing a human readable file format, bumping it by
two spaces when we enter a nested structure.

A> Not four spaces. Not eight spaces. Not a TAB.
A> 
A> Two spaces. Exactly two spaces. This is a magic number we can hardcode, because
A> every other number is **wrong**.

Finally, if we cannot request a `MonadReader` because our application does not
provide one, we can always return a `ReaderT`

{lang="text"}
~~~~~~~~
  def bearer(implicit F: Monad[F]): ReaderT[F, RefreshToken, BearerToken] =
    ReaderT( token => for {
    ...
~~~~~~~~

If a caller receives a `ReaderT`, and they have the `token` parameter to hand,
they can call `access.run(token)` and get back an `F[BearerToken]`.

Admittedly, since we don't have many callers, we should just revert to a regular
function parameter. `MonadReader` is of most use when:

1.  we may wish to refactor the code later to reload config
2.  the value is not needed by intermediate callers
3.  or, we want to locally scope some variable

Dotty can keep its implicit functions... we already have `ReaderT` and
`MonadReader`.


### `WriterT`

The opposite to reading is writing. The `WriterT` monad transformer is typically
for writing to a journal.

{lang="text"}
~~~~~~~~
  final case class WriterT[F[_], W, A](run: F[(W, A)])
  object WriterT {
    def put[F[_]: Functor, W, A](value: F[A])(w: W): WriterT[F, W, A] = ...
    def putWith[F[_]: Functor, W, A](value: F[A])(w: A => W): WriterT[F, W, A] = ...
    ...
  }
~~~~~~~~

The wrapped type is `F[(W, A)]` with the journal accumulated in `W`.

There is not just one associated monad, but two! `MonadTell` and `MonadListen`

{lang="text"}
~~~~~~~~
  @typeclass trait MonadTell[F[_], W] extends Monad[F] {
    def writer[A](w: W, v: A): F[A]
    def tell(w: W): F[Unit] = ...
  
    def :++>[A](fa: F[A])(w: =>W): F[A] = ...
    def :++>>[A](fa: F[A])(f: A => W): F[A] = ...
  }
  
  @typeclass trait MonadListen[F[_], W] extends MonadTell[F, W] {
    def listen[A](fa: F[A]): F[(A, W)]
  
    def written[A](fa: F[A]): F[W] = ...
  }
~~~~~~~~

`MonadTell` is for writing to the journal and `MonadListen` is to recover it.
The `WriterT` implementation is

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad, W: Monoid] = new MonadListen[WriterT[F, W, ?], W] {
    def point[A](a: =>A) = WriterT((Monoid[W].zero, a).point)
    def bind[A, B](fa: WriterT[F, W, A])(f: A => WriterT[F, W, B]) = WriterT(
      fa.run >>= { case (wa, a) => f(a).run.map { case (wb, b) => (wa |+| wb, b) } })
  
    def writer[A](w: W, v: A) = WriterT((w -> v).point)
    def listen[A](fa: WriterT[F, W, A]) = WriterT(
      fa.run.map { case (w, a) => (w, (a, w)) })
  }
~~~~~~~~

The most obvious example is to use `MonadTell` for logging, or audit reporting.
Reusing `Meta` from our error reporting we could imagine creating a log
structure like

{lang="text"}
~~~~~~~~
  sealed trait Log
  final case class Debug(msg: String)(implicit m: Meta)   extends Log
  final case class Info(msg: String)(implicit m: Meta)    extends Log
  final case class Warning(msg: String)(implicit m: Meta) extends Log
~~~~~~~~

and use `Dequeue[Log]` as our journal type. We could change our OAuth2
`authenticate` method to

{lang="text"}
~~~~~~~~
  def debug(msg: String)(implicit m: Meta): Dequeue[Log] = Dequeue(Debug(msg))
  
  def authenticate: F[CodeToken] =
    for {
      callback <- user.start :++> debug("started the webserver")
      params   = AuthRequest(callback, config.scope, config.clientId)
      url      = config.auth.withQuery(params.toUrlQuery)
      _        <- user.open(url) :++> debug(s"user visiting $url")
      code     <- user.stop :++> debug("stopped the webserver")
    } yield code
~~~~~~~~

We could even combine this with the `ReaderT` traces and get structured logs.

The caller can recover the logs with `.written` and do something with them.

However, there is a strong argument that logging deserves its own algebra. The
log level is often needed at the point of creation for performance reasons and
writing out the logs is typically managed at the application level rather than
something each component needs to be concerned about.

The `W` in `WriterT` has a `Monoid`, allowing us to journal any kind of
*monoidic* calculation as a secondary value along with our primary program. For
example, counting the number of times we do something, building up an
explanation of a calculation, or building up a `TradeTemplate` for a new trade
while we price it.

A popular specialisation of `WriterT` is when the monad is `Id`, meaning the
underlying `run` value is just a simple tuple `(W, A)`.

{lang="text"}
~~~~~~~~
  type Writer[W, A] = WriterT[Id, W, A]
  object WriterT {
    def writer[W, A](v: (W, A)): Writer[W, A] = WriterT[Id, W, A](v)
    def tell[W](w: W): Writer[W, Unit] = WriterT((w, ()))
    ...
  }
  final implicit class WriterOps[A](self: A) {
    def set[W](w: W): Writer[W, A] = WriterT(w -> self)
    def tell: Writer[A, Unit] = WriterT.tell(self)
  }
~~~~~~~~

which allows us to let any value carry around a secondary monoidal calculation,
without needing a context `F[_]`.

In a nutshell, `WriterT` / `MonadTell` is how to multi-task in FP.


### `StateT`

`StateT` lets us `.put`, `.get` and `.modify` a value that is handled by the
monadic context. It is the FP replacement of `var`.

If we were to write an impure method that has access to some mutable state, held
in a `var`, it might have the signature `() => F[A]` and return a different
value on every call, breaking referential transparency. With pure FP the
function takes the state as input and returns the updated state as output, which
is why the underlying type of `StateT` is `S => F[(S, A)]`.

The associated monad is `MonadState`

{lang="text"}
~~~~~~~~
  @typeclass trait MonadState[F[_], S] extends Monad[F] {
    def put(s: S): F[Unit]
    def get: F[S]
  
    def modify(f: S => S): F[Unit] = get >>= (s => put(f(s)))
    ...
  }
~~~~~~~~

A> `S` must be an immutable type: `.modify` is not an escape hatch to update a
A> mutable data structure. Mutability is impure and is only allowed within an `IO`
A> block.

`StateT` is implemented slightly differently than the monad transformers we have
studied so far. Instead of being a `case class` it is an ADT with two members:

{lang="text"}
~~~~~~~~
  sealed abstract class StateT[F[_], S, A]
  object StateT {
    def apply[F[_], S, A](f: S => F[(S, A)]): StateT[F, S, A] = Point(f)
  
    private final case class Point[F[_], S, A](
      run: S => F[(S, A)]
    ) extends StateT[F, S, A]
    private final case class FlatMap[F[_], S, A, B](
      a: StateT[F, S, A],
      f: (S, A) => StateT[F, S, B]
    ) extends StateT[F, S, B]
    ...
  }
~~~~~~~~

which are a specialised form of `Trampoline`, giving us stack safety when we
want to recover the underlying data structure, `.run`:

{lang="text"}
~~~~~~~~
  sealed abstract class StateT[F[_], S, A] {
    def run(initial: S)(implicit F: Monad[F]): F[(S, A)] = this match {
      case Point(f) => f(initial)
      case FlatMap(Point(f), g) =>
        f(initial) >>= { case (s, x) => g(s, x).run(s) }
      case FlatMap(FlatMap(f, g), h) =>
        FlatMap(f, (s, x) => FlatMap(g(s, x), h)).run(initial)
    }
    ...
  }
~~~~~~~~

`StateT` can straightforwardly implement `MonadState` with its ADT:

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Applicative, S] = new MonadState[StateT[F, S, ?], S] {
    def point[A](a: =>A) = Point(s => (s, a).point[F])
    def bind[A, B](fa: StateT[F, S, A])(f: A => StateT[F, S, B]) =
      FlatMap(fa, (_, a: A) => f(a))
  
    def get       = Point(s => (s, s).point[F])
    def put(s: S) = Point(_ => (s, ()).point[F])
  }
~~~~~~~~

With `.pure` mirrored on the companion as `.stateT`:

{lang="text"}
~~~~~~~~
  object StateT {
    def stateT[F[_]: Applicative, S, A](a: A): StateT[F, S, A] = ...
    ...
  }
~~~~~~~~

and `MonadTrans.liftM` providing the `F[A] => StateT[F, S, A]` constructor as
usual.

A common variant of `StateT` is when `F = Id`, giving the underlying type
signature `S => (S, A)`. Scalaz provides a type alias and convenience functions
for interacting with the `State` monad transformer directly, and mirroring
`MonadState`:

{lang="text"}
~~~~~~~~
  type State[a] = StateT[Id, a]
  object State {
    def apply[S, A](f: S => (S, A)): State[S, A] = StateT[Id, S, A](f)
    def state[S, A](a: A): State[S, A] = State((_, a))
  
    def get[S]: State[S, S] = State(s => (s, s))
    def put[S](s: S): State[S, Unit] = State(_ => (s, ()))
    def modify[S](f: S => S): State[S, Unit] = ...
    ...
  }
~~~~~~~~

For an example we can return to the business logic tests of
`drone-dynamic-agents`. Recall from Chapter 3 that we created `Mutable` as test
interpreters for our application and we stored the number of `started` and
`stoped` nodes in `var`.

{lang="text"}
~~~~~~~~
  class Mutable(state: WorldView) {
    var started, stopped: Int = 0
  
    implicit val drone: Drone[Id] = new Drone[Id] { ... }
    implicit val machines: Machines[Id] = new Machines[Id] { ... }
    val program = new DynAgentsModule[Id]
  }
~~~~~~~~

We now know that we can write a much better test simulator with `State`. We will
take the opportunity to upgrade the accuracy of the simulation at the same time.
Recall that a core domain object is our application's view of the world:

{lang="text"}
~~~~~~~~
  final case class WorldView(
    backlog: Int,
    agents: Int,
    managed: NonEmptyList[MachineNode],
    alive: Map[MachineNode, Epoch],
    pending: Map[MachineNode, Epoch],
    time: Epoch
  )
~~~~~~~~

Since we're writing a simulation of the world for our tests, we can create a
data type that captures the ground truth of everything

{lang="text"}
~~~~~~~~
  final case class World(
    backlog: Int,
    agents: Int,
    managed: NonEmptyList[MachineNode],
    alive: Map[MachineNode, Epoch],
    started: Set[MachineNode],
    stopped: Set[MachineNode],
    time: Epoch
  )
~~~~~~~~

A> We have not yet rewritten the application to fully make use Scalaz data types
A> and typeclasses, and we are still relying on stdlib collections. There is no
A> urgency to update as this is straightforward and these types can be used in a
A> pure FP manner.

The key difference being that the `started` and `stopped` nodes can be separated
out. Our interpreter can be implemented in terms of `State[World, a]` and we can
write our tests to assert on what both the `World` and `WorldView` looks like
after the business logic has run.

The interpreters, which are mocking out contacting external Drone and Google
services, may be implemented like this:

{lang="text"}
~~~~~~~~
  import State.{ get, modify }
  object StateImpl {
    type F[a] = State[World, a]
  
    private val D = new Drone[F] {
      def getBacklog: F[Int] = get.map(_.backlog)
      def getAgents: F[Int]  = get.map(_.agents)
    }
  
    private val M = new Machines[F] {
      def getAlive: F[Map[MachineNode, Epoch]]   = get.map(_.alive)
      def getManaged: F[NonEmptyList[MachineNode]] = get.map(_.managed)
      def getTime: F[Epoch]                      = get.map(_.time)
  
      def start(node: MachineNode): F[Unit] =
        modify(w => w.copy(started = w.started + node))
      def stop(node: MachineNode): F[Unit] =
        modify(w => w.copy(stopped = w.stopped + node))
    }
  
    val program = new DynAgentsModule[F](D, M)
  }
~~~~~~~~

and we can rewrite our tests to follow a convention where:

-   `world1` is the state of the world before running the program
-   `view1` is the application's belief about the world
-   `world2` is the state of the world after running the program
-   `view2` is the application's belief after running the program

For example,

{lang="text"}
~~~~~~~~
  it should "request agents when needed" in {
    val world1          = World(5, 0, managed, Map(), Set(), Set(), time1)
    val view1           = WorldView(5, 0, managed, Map(), Map(), time1)
  
    val (world2, view2) = StateImpl.program.act(view1).run(world1)
  
    view2.shouldBe(view1.copy(pending = Map(node1 -> time1)))
    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(Set(node1))
  }
~~~~~~~~

We would be forgiven for looking back to our business logic loop

{lang="text"}
~~~~~~~~
  state = initial()
  while True:
    state = update(state)
    state = act(state)
~~~~~~~~

and use `StateT` to manage the `state`. However, our `DynAgents` business logic
requires only `Applicative` and we would be violating the *Rule of Least Power*
to require the more powerful `MonadState`. It is therefore entirely reasonable
to handle the state manually by passing it in to `update` and `act`, and let
whoever calls us use a `StateT` if they wish.


### `IndexedStateT`

The code that we have studied thus far is not how Scalaz implements `StateT`.
Instead, a type alias points to `IndexedStateT`

{lang="text"}
~~~~~~~~
  type StateT[F[_], S, A] = IndexedStateT[F, S, S, A]
~~~~~~~~

The implementation of `IndexedStateT` is much as we have studied, with an extra
type parameter allowing the input state `S1` and output state `S2` to differ:

{lang="text"}
~~~~~~~~
  sealed abstract class IndexedStateT[F[_], -S1, S2, A] {
    def run(initial: S1)(implicit F: Bind[F]): F[(S2, A)] = ...
    ...
  }
  object IndexedStateT {
    def apply[F[_], S1, S2, A](
      f: S1 => F[(S2, A)]
    ): IndexedStateT[F, S1, S2, A] = Wrap(f)
  
    private final case class Wrap[F[_], S1, S2, A](
      run: S1 => F[(S2, A)]
    ) extends IndexedStateT[F, S1, S2, A]
    private final case class FlatMap[F[_], S1, S2, S3, A, B](
      a: IndexedStateT[F, S1, S2, A],
      f: (S2, A) => IndexedStateT[F, S2, S3, B]
    ) extends IndexedStateT[F, S1, S3, B]
    ...
  }
~~~~~~~~

`IndexedStateT` does not have a `MonadState` when `S1 != S2`, although it has a
`Monad`.

The following example is adapted from [Index your State](https://www.youtube.com/watch?v=JPVagd9W4Lo) by Vincent Marquez.
Consider the scenario where we must design an algebraic interface for an `Int`
to `String` lookup. This may have a networked implementation and the order of
calls is essential. Our first attempt at the API may look something like:

{lang="text"}
~~~~~~~~
  trait Cache[F[_]] {
    def read(k: Int): F[Maybe[String]]
  
    def lock: F[Unit]
    def update(k: Int, v: String): F[Unit]
    def commit: F[Unit]
  }
~~~~~~~~

with runtime errors if `.update` or `.commit` is called without a `.lock`. A
more complex design may involve multiple traits and a custom DSL that nobody
remembers how to use.

Instead, we can use `IndexedStateT` to require that the caller is in the correct
state. First we define our possible states as an ADT

{lang="text"}
~~~~~~~~
  sealed abstract class Status
  final case class Ready()                          extends Status
  final case class Locked(on: ISet[Int])            extends Status
  final case class Updated(values: Int ==>> String) extends Status
~~~~~~~~

and then revisit our algebra

{lang="text"}
~~~~~~~~
  trait Cache[M[_]] {
    type F[in, out, a] = IndexedStateT[M, in, out, a]
  
    def read(k: Int): F[Ready, Ready, Maybe[String]]
    def readLocked(k: Int): F[Locked, Locked, Maybe[String]]
    def readUncommitted(k: Int): F[Updated, Updated, Maybe[String]]
  
    def lock: F[Ready, Locked, Unit]
    def update(k: Int, v: String): F[Locked, Updated, Unit]
    def commit: F[Updated, Ready, Unit]
  }
~~~~~~~~

which will give a compiletime error if we try to `.update` without a `.lock`

{lang="text"}
~~~~~~~~
  for {
        a1 <- C.read(13)
        _  <- C.update(13, "wibble")
        _  <- C.commit
      } yield a1
  
  [error]  found   : IndexedStateT[M,Locked,Ready,Maybe[String]]
  [error]  required: IndexedStateT[M,Ready,?,?]
  [error]       _  <- C.update(13, "wibble")
  [error]          ^
~~~~~~~~

but allowing us to construct functions that can be composed by explicitly
including their state:

{lang="text"}
~~~~~~~~
  def wibbleise[M[_]: Monad](C: Cache[M]): F[Ready, Ready, String] =
    for {
      _  <- C.lock
      a1 <- C.readLocked(13)
      a2 = a1.cata(_ + "'", "wibble")
      _  <- C.update(13, a2)
      _  <- C.commit
    } yield a2
~~~~~~~~

A> We introduced code duplication in our API when we defined multiple `.read`
A> operations
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def read(k: Int): F[Ready, Ready, Maybe[String]]
A>   def readLocked(k: Int): F[Locked, Locked, Maybe[String]]
A>   def readUncommitted(k: Int): F[Updated, Updated, Maybe[String]]
A> ~~~~~~~~
A> 
A> Instead of
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def read[S <: Status](k: Int): F[S, S, Maybe[String]]
A> ~~~~~~~~
A> 
A> The reason we didn't do this is, *because subtyping*. This (broken) code would
A> compile with the inferred type signature `F[Nothing, Ready, Maybe[String]]`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   for {
A>     a1 <- C.read(13)
A>     _  <- C.update(13, "wibble")
A>     _  <- C.commit
A>   } yield a1
A> ~~~~~~~~
A> 
A> Scala has a `Nothing` type which is the subtype of all other types. Thankfully,
A> this code can not make it to runtime, as it would be impossible to call it, but
A> it is a bad API since users need to remember to add type ascriptions.
A> 
A> Another approach would be to stop the compiler from inferring `Nothing`. Scalaz
A> provides implicit evidence to assert that a type is not inferred as `Nothing`
A> and we can use it instead:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def read[S <: Status](k: Int)(implicit NN: NotNothing[S]): F[S, S, Maybe[String]]
A> ~~~~~~~~
A> 
A> The choice of which of the three alternative APIs to prefer is left to the
A> personal taste of the API designer.


### `IndexedReaderWriterStateT`

Those wanting to have a combination of `ReaderT`, `WriterT` and `IndexedStateT`
will not be disappointed. The transformer `IndexedReaderWriterStateT` wraps `(R,
S1) => F[(W, A, S2)]` with `R` having `Reader` semantics, `W` for monoidic
writes, and the `S` parameters for indexed state updates.

{lang="text"}
~~~~~~~~
  sealed abstract class IndexedReaderWriterStateT[F[_], -R, W, -S1, S2, A] {
    def run(r: R, s: S1)(implicit F: Monad[F]): F[(W, A, S2)] = ...
    ...
  }
  object IndexedReaderWriterStateT {
    def apply[F[_], R, W, S1, S2, A](f: (R, S1) => F[(W, A, S2)]) = ...
  }
  
  type ReaderWriterStateT[F[_], -R, W, S, A] = IndexedReaderWriterStateT[F, R, W, S, S, A]
  object ReaderWriterStateT {
    def apply[F[_], R, W, S, A](f: (R, S) => F[(W, A, S)]) = ...
  }
~~~~~~~~

Abbreviations are provided because otherwise, let's be honest, these types are
so long they look like they are part of a J2EE API:

{lang="text"}
~~~~~~~~
  type IRWST[F[_], -R, W, -S1, S2, A] = IndexedReaderWriterStateT[F, R, W, S1, S2, A]
  val IRWST = IndexedReaderWriterStateT
  type RWST[F[_], -R, W, S, A] = ReaderWriterStateT[F, R, W, S, A]
  val RWST = ReaderWriterStateT
~~~~~~~~

`IRWST` is a more efficient implementation than a manually created transformer
*stack* of `ReaderT[WriterT[IndexedStateT[F, ...], ...], ...]`.


### `TheseT`

`TheseT` allows errors to either abort the calculation or to be accumulated if
there is some partial success. Hence *keep calm and carry on*.

The underlying data type is `F[A \&/ B]` with `A` being the error type,
requiring a `Semigroup` to enable the accumulation of errors.

{lang="text"}
~~~~~~~~
  final case class TheseT[F[_], A, B](run: F[A \&/ B])
  object TheseT {
    def `this`[F[_]: Functor, A, B](a: F[A]): TheseT[F, A, B] = ...
    def that[F[_]: Functor, A, B](b: F[B]): TheseT[F, A, B] = ...
    def both[F[_]: Functor, A, B](ab: F[(A, B)]): TheseT[F, A, B] = ...
  
    implicit def monad[F[_]: Monad, A: Semigroup] = new Monad[TheseT[F, A, ?]] {
      def bind[B, C](fa: TheseT[F, A, B])(f: B => TheseT[F, A, C]) =
        TheseT(fa.run >>= {
          case This(a) => a.wrapThis[C].point[F]
          case That(b) => f(b).run
          case Both(a, b) =>
            f(b).run.map {
              case This(a_)     => (a |+| a_).wrapThis[C]
              case That(c_)     => Both(a, c_)
              case Both(a_, c_) => Both(a |+| a_, c_)
            }
        })
  
      def point[B](b: =>B) = TheseT(b.wrapThat.point[F])
    }
  }
~~~~~~~~

There is no special monad associated with `TheseT`, it is just a regular
`Monad`. If we wish to abort a calculation we can return a `This` value, but we
accumulate errors when we return a `Both` which also contains a successful part
of the calculation.

`TheseT` can also be thought of from a different angle: `A` does not need to be
an *error*. Similarly to `WriterT`, the `A` may be a secondary calculation that
we are computing along with the primary calculation `B`. `TheseT` allows early
exit when something special about `A` demands it, like when Charlie Bucket found
the last golden ticket (`A`) he threw away his chocolate bar (`B`).


### `ContT`

*Continuation Passing Style* (CPS) is a style of programming where functions
never return, instead *continuing* to the next computation. CPS is popular in
Javascript and Lisp as they allow non-blocking I/O via callbacks when data is
available. A direct translation of the pattern into impure Scala looks like

{lang="text"}
~~~~~~~~
  def foo[I, A](input: I)(next: A => Unit): Unit = next(doSomeStuff(input))
~~~~~~~~

We can make this pure by introducing an `F[_]` context

{lang="text"}
~~~~~~~~
  def foo[F[_], I, A](input: I)(next: A => F[Unit]): F[Unit]
~~~~~~~~

and refactor to return a function for the provided input

{lang="text"}
~~~~~~~~
  def foo[F[_], I, A](input: I): (A => F[Unit]) => F[Unit]
~~~~~~~~

`ContT` is just a container for this signature, with a `Monad` instance

{lang="text"}
~~~~~~~~
  final case class ContT[F[_], B, A](_run: (A => F[B]) => F[B]) {
    def run(f: A => F[B]): F[B] = _run(f)
  }
  object IndexedContT {
    implicit def monad[F[_], B] = new Monad[ContT[F, B, ?]] {
      def point[A](a: =>A) = ContT(_(a))
      def bind[A, C](fa: ContT[F, B, A])(f: A => ContT[F, B, C]) =
        ContT(c_fb => fa.run(a => f(a).run(c_fb)))
    }
  }
~~~~~~~~

and convenient syntax to create a `ContT` from a monadic value:

{lang="text"}
~~~~~~~~
  implicit class ContTOps[F[_]: Monad, A](self: F[A]) {
    def cps[B]: ContT[F, B, A] = ContT(a_fb => self >>= a_fb)
  }
~~~~~~~~

However, the simple callback use of continuations brings nothing to pure
functional programming because we already know how to sequence non-blocking,
potentially distributed, computations: that is what `Monad` is for and we can do
this with `.bind` or a `Kleisli` arrow. To see why continuations are useful we
need to consider a more complex example under a rigid design constraint.


#### Control Flow

Say we have modularised our application into components that can perform I/O,
with each component owned by a different development team:

{lang="text"}
~~~~~~~~
  final case class A0()
  final case class A1()
  final case class A2()
  final case class A3()
  final case class A4()
  
  def bar0(a4: A4): IO[A0] = ...
  def bar2(a1: A1): IO[A2] = ...
  def bar3(a2: A2): IO[A3] = ...
  def bar4(a3: A3): IO[A4] = ...
~~~~~~~~

Our goal is to produce an `A0` given an `A1`. Whereas Javascript and Lisp would
reach for continuations to solve this problem (because the I/O could block) we
can just chain the functions

{lang="text"}
~~~~~~~~
  def simple(a: A1): IO[A0] = bar2(a) >>= bar3 >>= bar4 >>= bar0
~~~~~~~~

We can lift `.simple` into its continuation form by using the convenient `.cps`
syntax and a little bit of extra boilerplate for each step:

{lang="text"}
~~~~~~~~
  def foo1(a: A1): ContT[IO, A0, A2] = bar2(a).cps
  def foo2(a: A2): ContT[IO, A0, A3] = bar3(a).cps
  def foo3(a: A3): ContT[IO, A0, A4] = bar4(a).cps
  
  def flow(a: A1): IO[A0]  = (foo1(a) >>= foo2 >>= foo3).run(bar0)
~~~~~~~~

So what does this buy us? Firstly, it is worth noting that the control flow of
this application is left to right

{width=60%}
![](images/contt-simple.png)

What if we are the authors of `foo2` and we want to post-process the `a0` that
we receive from the right (downstream), i.e. we want to split our `foo2` into
`foo2a` and `foo2b`

{width=75%}
![](images/contt-process1.png)

Add the constraint that we cannot change the definition of `flow` or `bar0`.
Perhaps it is not our code and is defined by the framework we are using.

It is not possible to process the output of `a0` by modifying any of the
remaining `barX` methods. However, with `ContT` we can modify `foo2` to process
the result of the `next` continuation:

{width=45%}
![](images/contt-process2.png)

Which can be defined with

{lang="text"}
~~~~~~~~
  def foo2(a: A2): ContT[IO, A0, A3] = ContT { next =>
    for {
      a3  <- bar3(a)
      a0  <- next(a3)
    } yield process(a0)
  }
~~~~~~~~

We are not limited to `.map` over the return value, we can `.bind` into another
control flow turning the linear flow into a graph!

{width=50%}
![](images/contt-elsewhere.png)

{lang="text"}
~~~~~~~~
  def elsewhere: ContT[IO, A0, A4] = ???
  def foo2(a: A2): ContT[IO, A0, A3] = ContT { next =>
    for {
      a3  <- bar3(a)
      a0  <- next(a3)
      a0_ <- if (check(a0)) a0.pure[IO]
             else elsewhere.run(bar0)
    } yield a0_
  }
~~~~~~~~

Or we can stay within the original flow and retry everything downstream

{width=45%}
![](images/contt-retry.png)

{lang="text"}
~~~~~~~~
  def foo2(a: A2): ContT[IO, A0, A3] = ContT { next =>
    for {
      a3  <- bar3(a)
      a0  <- next(a3)
      a0_ <- if (check(a0)) a0.pure[IO]
             else next(a3)
    } yield a0_
  }
~~~~~~~~

This is just one retry, not an infinite loop. For example, we might want
downstream to reconfirm a potentially dangerous action.

Finally, we can perform actions that are specific to the context of the `ContT`,
in this case `IO` which lets us do error handling and resource cleanup:

{lang="text"}
~~~~~~~~
  def foo2(a: A2): ContT[IO, A0, A3] = bar3(a).ensuring(cleanup).cps
~~~~~~~~


#### When to Order Spaghetti

It is not an accident that these diagrams look like spaghetti, that is just what
happens when we start messing with control flow. All the mechanisms we've
discussed in this section are simple to implement directly if we can edit the
definition of `flow`, therefore we do not typically need to use `ContT`.

However, if we are designing a framework, we should consider exposing the plugin
system as `ContT` callbacks to allow our users more power over their control
flow. Sometimes the customer just really wants the spaghetti.

For example, if the Scala compiler was written using CPS, it would allow for a
principled approach to communication between compiler phases. A compiler plugin
would be able to perform some action based on the inferred type of an
expression, computed at a later stage in the compile. Similarly, continuations
would be a good API for an extensible build tool or text editor.

A caveat with `ContT` is that it is not stack safe, so cannot be used for
programs that run forever.


#### Great, kid. Don't get `ContT`.

A more complex variant of `ContT` called `IndexedContT` wraps `(A => F[B]) =>
F[C]`. The new type parameter `C` allows the return type of the entire
computation to be different to the return type between each component. But if
`B` is not equal to `C` then there is no `Monad`.

Not missing an opportunity to generalise as much as possible, `IndexedContT` is
actually implemented in terms of an even more general structure (note the extra
`s` before the `T`)

{lang="text"}
~~~~~~~~
  final case class IndexedContsT[W[_], F[_], C, B, A](_run: W[A => F[B]] => F[C])
  
  type IndexedContT[f[_], c, b, a] = IndexedContsT[Id, f, c, b, a]
  type ContT[f[_], b, a]           = IndexedContsT[Id, f, b, b, a]
  type ContsT[w[_], f[_], b, a]    = IndexedContsT[w, f, b, b, a]
  type Cont[b, a]                  = IndexedContsT[Id, Id, b, b, a]
~~~~~~~~

where `W[_]` has a `Comonad`, and `ContT` is actually implemented as a type
alias. Companion objects exist for these type aliases with convenient
constructors.

Admittedly, five type parameters is perhaps a generalisation too far. But then
again, over-generalisation is consistent with the sensibilities of
continuations.


### Transformer Stacks and Ambiguous Implicits

This concludes our tour of the monad transformers in Scalaz.

When multiple transformers are combined, we call this a *transformer stack* and
although it is verbose, it is possible to read off the features by reading the
transformers. For example if we construct an `F[_]` context which is a set of
composed transformers, such as

{lang="text"}
~~~~~~~~
  type Ctx[A] = StateT[EitherT[IO, E, ?], S, A]
~~~~~~~~

we know that we are adding error handling with error type `E` (there is a
`MonadError[Ctx, E]`) and we are managing state `A` (there is a `MonadState[Ctx,
S]`).

But there are unfortunately practical drawbacks to using monad transformers and
their companion `Monad` typeclasses:

1.  Multiple implicit `Monad` parameters mean that the compiler cannot find the
    correct syntax to use for the context.

2.  Monads do not compose in the general case, which means that the order of
    nesting of the transformers is important.

3.  All the interpreters must be lifted into the common context. For example, we
    might have an implementation of some algebra that uses for `IO` and now we
    need to wrap it with `StateT` and `EitherT` even though they are unused
    inside the interpreter.

4.  There is a performance cost associated to each layer. And some monad
    transformers are worse than others. `StateT` is particularly bad but even
    `EitherT` can cause memory allocation problems for high throughput
    applications.

We need to talk about workarounds.


#### No Syntax

Say we have an algebra

{lang="text"}
~~~~~~~~
  trait Lookup[F[_]] {
    def look: F[Int]
  }
~~~~~~~~

and some data types

{lang="text"}
~~~~~~~~
  final case class Problem(bad: Int)
  final case class Table(last: Int)
~~~~~~~~

that we want to use in our business logic

{lang="text"}
~~~~~~~~
  def foo[F[_]](L: Lookup[F])(
    implicit
      E: MonadError[F, Problem],
      S: MonadState[F, Table]
  ): F[Int] = for {
    old <- S.get
    i   <- L.look
    _   <- if (i === old.last) E.raiseError(Problem(i))
           else ().pure[F]
  } yield i
~~~~~~~~

The first problem we encounter is that this fails to compile

{lang="text"}
~~~~~~~~
  [error] value flatMap is not a member of type parameter F[Table]
  [error]     old <- S.get
  [error]              ^
~~~~~~~~

There are some tactical solutions to this problem. The most obvious is to make
all the parameters explicit

{lang="text"}
~~~~~~~~
  def foo1[F[_]: Monad](
    L: Lookup[F],
    E: MonadError[F, Problem],
    S: MonadState[F, Table]
  ): F[Int] = ...
~~~~~~~~

and require only `Monad` to be passed implicitly via context bounds. However,
this means that we must manually wire up the `MonadError` and `MonadState` when
calling `foo1` and when calling out to another method that requires an
`implicit`.

A second solution is to leave the parameters `implicit` and use name shadowing
to make all but one of the parameters explicit. This allows upstream to use
implicit resolution when calling us but we still need to pass parameters
explicitly if we call out.

{lang="text"}
~~~~~~~~
  @inline final def shadow[A, B, C](a: A, b: B)(f: (A, B) => C): C = f(a, b)
  
  def foo2a[F[_]: Monad](L: Lookup[F])(
    implicit
    E: MonadError[F, Problem],
    S: MonadState[F, Table]
  ): F[Int] = shadow(E, S) { (E, S) => ...
~~~~~~~~

or we could shadow just one `Monad`, leaving the other one to provide our syntax
and to be available for when we call out to other methods

{lang="text"}
~~~~~~~~
  @inline final def shadow[A, B](a: A)(f: A => B): B = f(a)
  ...
  
  def foo2b[F[_]](L: Lookup[F])(
    implicit
    E: MonadError[F, Problem],
    S: MonadState[F, Table]
  ): F[Int] = shadow(E) { E => ...
~~~~~~~~

A third option, with a higher up-front cost, is to create a custom `Monad`
typeclass that holds `implicit` references to the two `Monad` classes that we
care about

{lang="text"}
~~~~~~~~
  trait MonadErrorState[F[_], E, S] {
    implicit def E: MonadError[F, E]
    implicit def S: MonadState[F, S]
  }
~~~~~~~~

and a derivation of the typeclass given a `MonadError` and `MonadState`

{lang="text"}
~~~~~~~~
  object MonadErrorState {
    implicit def create[F[_], E, S](
      implicit
        E0: MonadError[F, E],
        S0: MonadState[F, S]
    ) = new MonadErrorState[F, E, S] {
      def E: MonadError[F, E] = E0
      def S: MonadState[F, S] = S0
    }
  }
~~~~~~~~

Now if we want access to `S` or `E` we get them via `F.S` or `F.E`

{lang="text"}
~~~~~~~~
  def foo3a[F[_]: Monad](L: Lookup[F])(
    implicit F: MonadErrorState[F, Problem, Table]
  ): F[Int] =
    for {
      old <- F.S.get
      i   <- L.look
      _ <- if (i === old.last) F.E.raiseError(Problem(i))
      else ().pure[F]
    } yield i
~~~~~~~~

Like the second solution, we can choose one of the `Monad` instances to be
`implicit` within the block, achieved by importing it

{lang="text"}
~~~~~~~~
  def foo3b[F[_]](L: Lookup[F])(
    implicit F: MonadErrorState[F, Problem, Table]
  ): F[Int] = {
    import F.E
    ...
  }
~~~~~~~~


#### Composing Transformers

An `EitherT[StateT[...], ...]` has a `MonadError` but does not have a
`MonadState`, whereas `StateT[EitherT[...], ...]` can provide both.

The workaround is to study the implicit derivations on the companion of the
transformers and to make sure that the outer most transformer provides
everything we need.

A rule of thumb is that more complex transformers go on the outside, with this
chapter presenting transformers in increasing order of complex.


#### Lifting Interpreters

Continuing the same example, let's say our `Lookup` algebra has an `IO`
interpreter

{lang="text"}
~~~~~~~~
  object LookupRandom extends Lookup[IO] {
    def look: IO[Int] = IO { util.Random.nextInt }
  }
~~~~~~~~

but we want our context to be

{lang="text"}
~~~~~~~~
  type Ctx[A] = StateT[EitherT[IO, Problem, ?], Table, A]
~~~~~~~~

to give us a `MonadError` and a `MonadState`. This means we need to wrap
`LookupRandom` to operate over `Ctx`.

A> The odds of getting the types correct on the first attempt are approximately
A> 3,720 to one.

Firstly, we want to make use of the `.liftM` syntax on `Monad`, which uses
`MonadTrans` to lift from our starting `F[A]` into `G[F, A]`

{lang="text"}
~~~~~~~~
  final class MonadOps[F[_]: Monad, A](fa: F[A]) {
    def liftM[G[_[_], _]: MonadTrans]: G[F, A] = ...
    ...
  }
~~~~~~~~

It is important to realise that the type parameters to `.liftM` have two type
holes, one of shape `_[_]` and another of shape `_`. If we create type aliases
of this shape

{lang="text"}
~~~~~~~~
  type Ctx0[F[_], A] = StateT[EitherT[F, Problem, ?], Table, A]
  type Ctx1[F[_], A] = EitherT[F, Problem, A]
  type Ctx2[F[_], A] = StateT[F, Table, A]
~~~~~~~~

We can abstract over `MonadTrans` to lift a `Lookup[F]` to any `Lookup[G[F, ?]]`
where `G` is a Monad Transformer:

{lang="text"}
~~~~~~~~
  def liftM[F[_]: Monad, G[_[_], _]: MonadTrans](f: Lookup[F]) =
    new Lookup[G[F, ?]] {
      def look: G[F, Int] = f.look.liftM[G]
    }
~~~~~~~~

Allowing us to wrap once for `EitherT`, and then again for `StateT`

{lang="text"}
~~~~~~~~
  val wrap1 = Lookup.liftM[IO, Ctx1](LookupRandom)
  val wrap2: Lookup[Ctx] = Lookup.liftM[EitherT[IO, Problem, ?], Ctx2](wrap1)
~~~~~~~~

Another way to achieve this, in a single step, is to use `MonadIO` which enables
lifting an `IO` into a transformer stack:

{lang="text"}
~~~~~~~~
  @typeclass trait MonadIO[F[_]] extends Monad[F] {
    def liftIO[A](ioa: IO[A]): F[A]
  }
~~~~~~~~

with `MonadIO` instances for all the common combinations of transformers.

The boilerplate overhead to lift an `IO` interpreter to anything with a
`MonadIO` instance is therefore two lines of code (for the interpreter
definition), plus one line per element of the algebra, and a final line to call
it:

{lang="text"}
~~~~~~~~
  def liftIO[F[_]: MonadIO](io: Lookup[IO]) = new Lookup[F] {
    def look: F[Int] = io.look.liftIO[F]
  }
  
  val L: Lookup[Ctx] = Lookup.liftIO(LookupRandom)
~~~~~~~~

A> A compiler plugin that automatically produces `.liftM`, `.liftIO`, and
A> additional boilerplate that arises in this chapter, would be a great
A> contribution to the ecosystem!


#### Performance

The biggest problem with Monad Transformers is their performance overhead.
`EitherT` has a reasonably low overhead, with every `.flatMap` call generating a
handful of objects, but this can impact high throughput applications where every
object allocation matters. Other transformers, such as `StateT`, effectively add
a trampoline, and `ContT` keeps the entire call-chain retained in memory.

A> Some applications do not care about allocations if they are bounded by network
A> or I/O. Always measure.

If performance becomes a problem, the solution is to not use Monad Transformers.
At least not the transformer data structures. A big advantage of the `Monad`
typeclasses, like `MonadState` is that we can create an optimised `F[_]` for our
application that provides the typeclasses naturally. We will learn how to create
an optimal `F[_]` over the next two chapters, when we deep dive into two
structures which we have already seen: `Free` and `IO`.


## A Free Lunch

Our industry craves safe high-level languages, trading developer efficiency and
reliability for reduced runtime performance.

The Just In Time (JIT) compiler on the JVM performs so well that simple
functions can have comparable performance to their C or C++ equivalents,
ignoring the cost of garbage collection. However, the JIT only performs *low
level optimisations*: branch prediction, inlining methods, unrolling loops, and
so on.

The JIT does not perform optimisations of our business logic, for example
batching network calls or parallelising independent tasks. The developer is
responsible for writing the business logic and optimisations at the same time,
reducing readability and making it harder to maintain. It would be good if
optimisation was a tangential concern.

If instead, we have a data structure that describes our business logic in terms
of high level concepts, not machine instructions, we can perform *high level
optimisation*. Data structures of this nature are typically called *Free*
structures and can be generated for free for the members of the algebraic
interfaces of our program. For example, a *Free Applicative* can be generated
that allows us to batch or de-duplicate expensive network I/O.

In this section we will learn how to create free structures, and how they can be
used.


### `Free` (`Monad`)

Fundamentally, a monad describes a sequential program where every step depends
on the previous one. We are therefore limited to modifications that only know
about things that we've already run and the next thing we are going to run.

A> It was trendy, circa 2015, to write FP programs in terms of `Free` so this is as
A> much an exercise in how to understand `Free` code as it is to be able to write
A> or use it.
A> 
A> There is a lot of boilerplate to create a free structure. We shall use this
A> study of `Free` to learn how to generate the boilerplate.

As a refresher, `Free` is the data structure representation of a `Monad` and is
defined by three members

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A] {
    def mapSuspension[T[_]](f: S ~> T): Free[T, A] = ...
    def foldMap[M[_]: Monad](f: S ~> M): M[A] = ...
    ...
  }
  object Free {
    implicit def monad[S[_], A]: Monad[Free[S, A]] = ...
  
    private final case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
    private final case class Return[S[_], A](a: A)     extends Free[S, A]
    private final case class Gosub[S[_], A0, B](
      a: Free[S, A0],
      f: A0 => Free[S, B]
    ) extends Free[S, B] { type A = A0 }
  
    def liftF[S[_], A](value: S[A]): Free[S, A] = Suspend(value)
    ...
  }
~~~~~~~~

-   `Suspend` represents a program that has not yet been interpreted
-   `Return` is `.pure`
-   `Gosub` is `.bind`

A `Free[S, A]` can be *freely generated* for any algebra `S`. To make this
explicit, consider our application's `Machines` algebra

{lang="text"}
~~~~~~~~
  trait Machines[F[_]] {
    def getTime: F[Epoch]
    def getManaged: F[NonEmptyList[MachineNode]]
    def getAlive: F[Map[MachineNode, Epoch]]
    def start(node: MachineNode): F[Unit]
    def stop(node: MachineNode): F[Unit]
  }
~~~~~~~~

We define a freely generated `Free` for `Machines` by creating an ADT with a
data type for each element of the algebra. Each data type has the same input
parameters as its corresponding element, is parameterised over the return type,
and has the same name:

{lang="text"}
~~~~~~~~
  object Machines {
    sealed abstract class Ast[A]
    final case class GetTime()                extends Ast[Epoch]
    final case class GetManaged()             extends Ast[NonEmptyList[MachineNode]]
    final case class GetAlive()               extends Ast[Map[MachineNode, Epoch]]
    final case class Start(node: MachineNode) extends Ast[Unit]
    final case class Stop(node: MachineNode)  extends Ast[Unit]
    ...
~~~~~~~~

The ADT defines an Abstract Syntax Tree (AST) because each member is
representing a computation in a program.

W> The freely generated `Free` for `Machines` is `Free[Machines.Ast, ?]`, i.e. for
W> the AST, not `Free[Machines, ?]`. It is easy to make a mistake, since the latter
W> will compile, but is meaningless.

We then define `.liftF`, an implementation of `Machines`, with `Free[Ast, ?]` as
the context. Every method simply delegates to `Free.liftT` to create a `Suspend`

{lang="text"}
~~~~~~~~
  ...
    def liftF = new Machines[Free[Ast, ?]] {
      def getTime = Free.liftF(GetTime())
      def getManaged = Free.liftF(GetManaged())
      def getAlive = Free.liftF(GetAlive())
      def start(node: MachineNode) = Free.liftF(Start(node))
      def stop(node: MachineNode) = Free.liftF(Stop(node))
    }
  }
~~~~~~~~

When we construct our program, parameterised over a `Free`, we run it by
providing an *interpreter* (a natural transformation `Ast ~> M`) to the
`.foldMap` method. For example, if we could provide an interpreter that maps to
`IO` we can construct an `IO[Unit]` program via the free AST

{lang="text"}
~~~~~~~~
  def program[F[_]: Monad](M: Machines[F]): F[Unit] = ...
  
  val interpreter: Machines.Ast ~> IO = ...
  
  val app: IO[Unit] = program[Free[Machines.Ast, ?]](Machines.liftF)
                        .foldMap(interpreter)
~~~~~~~~

For completeness, an interpreter that delegates to a direct implementation is
easy to write. This might be useful if the rest of the application is using
`Free` as the context and we already have an `IO` implementation that we want to
use:

{lang="text"}
~~~~~~~~
  def interpreter[F[_]](f: Machines[F]): Ast ~> F = λ[Ast ~> F] {
    case GetTime()    => f.getTime
    case GetManaged() => f.getManaged
    case GetAlive()   => f.getAlive
    case Start(node)  => f.start(node)
    case Stop(node)   => f.stop(node)
  }
~~~~~~~~

But our business logic needs more than just `Machines`, we also need access to
the `Drone` algebra, recall defined as

{lang="text"}
~~~~~~~~
  trait Drone[F[_]] {
    def getBacklog: F[Int]
    def getAgents: F[Int]
  }
  object Drone {
    sealed abstract class Ast[A]
    ...
    def liftF = ...
    def interpreter = ...
  }
~~~~~~~~

What we want is for our AST to be a combination of the `Machines` and `Drone`
ASTs. We studied `Coproduct` in Chapter 6, a higher kinded disjunction:

{lang="text"}
~~~~~~~~
  final case class Coproduct[F[_], G[_], A](run: F[A] \/ G[A])
~~~~~~~~

We can use the context `Free[Coproduct[Machines.Ast, Drone.Ast, ?], ?]`.

We could manually create the coproduct but we would be swimming in boilerplate,
and we'd have to do it all again if we wanted to add a third algebra.

The `scalaz.Inject` typeclass helps:

{lang="text"}
~~~~~~~~
  type :<:[F[_], G[_]] = Inject[F, G]
  sealed abstract class Inject[F[_], G[_]] {
    def inj[A](fa: F[A]): G[A]
    def prj[A](ga: G[A]): Option[F[A]]
  }
  object Inject {
    implicit def left[F[_], G[_]]: F :<: Coproduct[F, G, ?]] = ...
    ...
  }
~~~~~~~~

The `implicit` derivations generate `Inject` instances when we need them,
letting us rewrite our `liftF` to work for any combination of ASTs:

{lang="text"}
~~~~~~~~
  def liftF[F[_]](implicit I: Ast :<: F) = new Machines[Free[F, ?]] {
    def getTime                  = Free.liftF(I.inj(GetTime()))
    def getManaged               = Free.liftF(I.inj(GetManaged()))
    def getAlive                 = Free.liftF(I.inj(GetAlive()))
    def start(node: MachineNode) = Free.liftF(I.inj(Start(node)))
    def stop(node: MachineNode)  = Free.liftF(I.inj(Stop(node)))
  }
~~~~~~~~

It is nice that `F :<: G` reads as if our `Ast` is a member of the complete `F`
instruction set: this syntax is intentional.

A> A compiler plugin that automatically produces the `scalaz.Free` boilerplate
A> would be a great contribution to the ecosystem! Not only is it painful to write
A> the boilerplate, but there is the potential for a typo to ruin our day: if two
A> members of the algebra have the same type signature, we might not notice.

Putting it all together, lets say we have a program that we wrote abstracting over `Monad`

{lang="text"}
~~~~~~~~
  def program[F[_]: Monad](M: Machines[F], D: Drone[F]): F[Unit] = ...
~~~~~~~~

and we have some existing implementations of `Machines` and `Drone`, we can
create interpreters from them:

{lang="text"}
~~~~~~~~
  val MachinesIO: Machines[IO] = ...
  val DroneIO: Drone[IO]       = ...
  
  val M: Machines.Ast ~> IO = Machines.interpreter(MachinesIO)
  val D: Drone.Ast ~> IO    = Drone.interpreter(DroneIO)
~~~~~~~~

and combine them into the larger instruction set using a convenience method from
the `NaturalTransformation` companion

{lang="text"}
~~~~~~~~
  object NaturalTransformation {
    def or[F[_], G[_], H[_]](fg: F ~> G, hg: H ~> G): Coproduct[F, H, ?] ~> G = ...
    ...
  }
  
  type Ast[a] = Coproduct[Machines.Ast, Drone.Ast, a]
  
  val interpreter: Ast ~> IO = NaturalTransformation.or(M, D)
~~~~~~~~

Then use it to produce an `IO`

{lang="text"}
~~~~~~~~
  val app: IO[Unit] = program[Free[Ast, ?]](Machines.liftF, Drone.liftF)
                        .foldMap(interpreter)
~~~~~~~~

But we've gone in circles! We could have used `IO` as the context for our
program in the first place and avoided `Free`. So why did we put ourselves
through all this pain? Here are some examples of where `Free` might be useful.


#### Testing: Mocks and Stubs

It might sound hypocritical to propose that `Free` can be used to reduce
boilerplate, given how much code we have written. However, there is a tipping
point where the `Ast` pays for itself when we have many tests that require stub
implementations.

If the `.Ast` and `.liftF` is defined for an algebra, we can create *partial
interpreters*

{lang="text"}
~~~~~~~~
  val M: Machines.Ast ~> Id = stub[Map[MachineNode, Epoch]] {
    case Machines.GetAlive() => Map.empty
  }
  val D: Drone.Ast ~> Id = stub[Int] {
    case Drone.GetBacklog() => 1
  }
~~~~~~~~

which can be used to test our `program`

{lang="text"}
~~~~~~~~
  program[Free[Ast, ?]](Machines.liftF, Drone.liftF)
    .foldMap(or(M, D))
    .shouldBe(1)
~~~~~~~~

By using partial functions, and not total functions, we are exposing ourselves
to runtime errors. Many teams are happy to accept this risk in their unit tests
since the test would fail if there is a programmer error.

Arguably we could also achieve the same thing with implementations of our
algebras that implement every method with `???`, overriding what we need on a
case by case basis.

A> The library [smock](https://github.com/djspiewak/smock) is more powerful, but for the purposes of this short example
A> we can define `stub` ourselves using a type inference trick that can be found
A> all over the Scalaz source code. The reason for `Stub` being a separate class is
A> so that we only need to provide the `A` type parameter, with `F` and `G`
A> inferred from the left hand side of the expression:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   object Mocker {
A>     final class Stub[A] {
A>       def apply[F[_], G[_]](pf: PartialFunction[F[A], G[A]]): F ~> G = new (F ~> G) {
A>         def apply[α](fa: F[α]) = pf.asInstanceOf[PartialFunction[F[α], G[α]]](fa)
A>       }
A>     }
A>     def stub[A]: Stub[A] = new Stub[A]
A>   }
A> ~~~~~~~~


#### Monitoring

It is typical for server applications to be monitored by runtime agents that
manipulate bytecode to insert profilers and extract various kinds of usage or
performance information.

If our application's context is `Free`, we do not need to resort to bytecode
manipulation, we can instead implement a side-effecting monitor as an
interpreter that we have complete control over.

A> Runtime introspection is one of the few cases that can justify use of a
A> side-effect. If the monitoring is not visible to the program itself, referential
A> transparency will still hold. This is also the argument used by teams that use
A> side-effecting debug logging, and our argument for allowing mutation in the
A> implementation of `Memo`.

For example, consider using this `Ast ~> Ast` "agent"

{lang="text"}
~~~~~~~~
  val Monitor = λ[Demo.Ast ~> Demo.Ast](
    _.run match {
      case \/-(m @ Drone.GetBacklog()) =>
        JmxAbstractFactoryBeanSingletonProviderUtilImpl.count("backlog")
        Coproduct.rightc(m)
      case other =>
        Coproduct(other)
    }
  )
~~~~~~~~

which records method invocations: we would use a vendor-specific routine in real
code. We could also watch for specific messages of interest and log them as a
debugging aid.

We can attach `Monitor` to our production `Free` application with

{lang="text"}
~~~~~~~~
  .mapSuspension(Monitor).foldMap(interpreter)
~~~~~~~~

or combine the natural transformations and run with a single

{lang="text"}
~~~~~~~~
  .foldMap(Monitor.andThen(interpreter))
~~~~~~~~


#### Monkey Patching

As engineers, we are used to requests for bizarre workarounds to be added to the
core logic of the application. We might want to codify such corner cases as
*exceptions to the rule* and handle them tangentially to our core logic.

For example, suppose we get a memo from accounting telling us

> *URGENT: Bob is using node `#c0ffee` to run the year end. DO NOT STOP THIS
> MACHINE!1!*

There is no possibility to discuss why Bob shouldn't be using our machines for
his super-important accounts, so we have to hack our business logic and put out
a release to production as soon as possible.

Our monkey patch can map into a `Free` structure, allowing us to return a
pre-canned result (`Free.pure`) instead of scheduling the instruction. We
special case the instruction in a custom natural transformation with its return
value:

{lang="text"}
~~~~~~~~
  val monkey = λ[Machines.Ast ~> Free[Machines.Ast, ?]] {
    case Machines.Stop(MachineNode("#c0ffee")) => Free.pure(())
    case other                                 => Free.liftF(other)
  }
~~~~~~~~

eyeball that it works, push it to prod, and set an alarm for next week to remind
us to remove it, and revoke Bob's access to our servers.

Our unit test could use `State` as the target context, so we can keep track of
all the nodes we stopped:

{lang="text"}
~~~~~~~~
  type S = Set[MachineNode]
  val M: Machines.Ast ~> State[S, ?] = Mocker.stub[Unit] {
    case Machines.Stop(node) => State.modify[S](_ + node)
  }
  
  Machines
    .liftF[Machines.Ast]
    .stop(MachineNode("#c0ffee"))
    .foldMap(monkey)
    .foldMap(M)
    .exec(Set.empty)
    .shouldBe(Set.empty)
~~~~~~~~

along with a test that "normal" nodes are not affected.

An advantage of using `Free` to avoid stopping the `#c0ffee` nodes is that we
can be sure to catch all the usages instead of having to go through the business
logic and look for all usages of `.stop`. If our application context is just an
`IO` we could, of course, implement this logic in the `Machines[IO]`
implementation but an advantage of using `Free` is that we don't need to touch
the existing code and can instead isolate and test this (temporary) behaviour,
without being tied to the `IO` implementations.


### `FreeAp` (`Applicative`)

Despite this chapter being called **Advanced Monads**, the takeaway is: *we
shouldn't use monads unless we really **really** have to*. In this section, we
will see why `FreeAp` (free applicative) is preferable to `Free` monads.

`FreeAp` is defined as the data structure representation of the `ap` and `pure`
methods from the `Applicative` typeclass:

{lang="text"}
~~~~~~~~
  sealed abstract class FreeAp[S[_], A] {
    def hoist[G[_]](f: S ~> G): FreeAp[G,A] = ...
    def foldMap[G[_]: Applicative](f: S ~> G): G[A] = ...
    def monadic: Free[S, A] = ...
    def analyze[M:Monoid](f: F ~> λ[α => M]): M = ...
    ...
  }
  object FreeAp {
    implicit def applicative[S[_], A]: Applicative[FreeAp[S, A]] = ...
  
    private final case class Pure[S[_], A](a: A) extends FreeAp[S, A]
    private final case class Ap[S[_], A, B](
      value: () => S[B],
      function: () => FreeAp[S, B => A]
    ) extends FreeAp[S, A]
  
    def pure[S[_], A](a: A): FreeAp[S, A] = Pure(a)
    def lift[S[_], A](x: =>S[A]): FreeAp[S, A] = ...
    ...
  }
~~~~~~~~

The methods `.hoist` and `.foldMap` are like their `Free` analogues
`.mapSuspension` and `.foldMap`.

As a convenience, we can generate a `Free[S, A]` from our `FreeAp[S, A]` with
`.monadic`. This is especially useful to optimise smaller `Applicative`
subsystems yet use them as part of a larger `Free` program.

Like `Free`, we must create a `FreeAp` for our ASTs, more boilerplate...

{lang="text"}
~~~~~~~~
  def liftA[F[_]](implicit I: Ast :<: F) = new Machines[FreeAp[F, ?]] {
    def getTime = FreeAp.lift(I.inj(GetTime()))
    ...
  }
~~~~~~~~


#### Batching Network Calls

We opened this chapter with grand claims about performance. Time to deliver.

[Philip Stark](https://gist.github.com/hellerbarde/2843375#file-latency_humanized-markdown)'s Humanised version of [Peter Norvig's Latency Numbers](http://norvig.com/21-days.html#answers) serve as
motivation for why we should focus on reducing network calls to optimise an
application:

| Computer                          | Human Timescale | Human Analogy                  |
|--------------------------------- |--------------- |------------------------------ |
| L1 cache reference                | 0.5 secs        | One heart beat                 |
| Branch mispredict                 | 5 secs          | Yawn                           |
| L2 cache reference                | 7 secs          | Long yawn                      |
| Mutex lock/unlock                 | 25 secs         | Making a cup of tea            |
| Main memory reference             | 100 secs        | Brushing your teeth            |
| Compress 1K bytes with Zippy      | 50 min          | Scala compiler CI pipeline     |
| Send 2K bytes over 1Gbps network  | 5.5 hr          | Train London to Edinburgh      |
| SSD random read                   | 1.7 days        | Weekend                        |
| Read 1MB sequentially from memory | 2.9 days        | Long weekend                   |
| Round trip within same datacenter | 5.8 days        | Long US Vacation               |
| Read 1MB sequentially from SSD    | 11.6 days       | Short EU Holiday               |
| Disk seek                         | 16.5 weeks      | Term of university             |
| Read 1MB sequentially from disk   | 7.8 months      | Fully paid maternity in Norway |
| Send packet CA->Netherlands->CA   | 4.8 years       | Government's term              |

Although `Free` and `FreeAp` incur a memory allocation overhead, the equivalent
of 100 seconds in the humanised chart, every time we can turn two sequential
network calls into one batch call, we save nearly 5 years.

When we are in a `Applicative` context, we can safely optimise our application
without breaking any of the expectations of the original program, and without
cluttering the business logic.

Luckily, our main business logic only requires an `Applicative`, recall

{lang="text"}
~~~~~~~~
  final class DynAgentsModule[F[_]: Applicative](D: Drone[F], M: Machines[F])
      extends DynAgents[F] {
    def act(world: WorldView): F[WorldView] = ...
    ...
  }
~~~~~~~~

To begin, we create the `lift` boilerplate for a new `Batch` algebra

{lang="text"}
~~~~~~~~
  trait Batch[F[_]] {
    def start(nodes: NonEmptyList[MachineNode]): F[Unit]
  }
  object Batch {
    sealed abstract class Ast[A]
    final case class Start(nodes: NonEmptyList[MachineNode]) extends Ast[Unit]
  
    def liftA[F[_]](implicit I: Ast :<: F) = new Batch[FreeAp[F, ?]] {
      def start(nodes: NonEmptyList[MachineNode]) = FreeAp.lift(I.inj(Start(nodes)))
    }
  }
~~~~~~~~

and then we will create an instance of `DynAgentsModule` with `FreeAp` as the context

{lang="text"}
~~~~~~~~
  type Orig[a] = Coproduct[Machines.Ast, Drone.Ast, a]
  
  val world: WorldView = ...
  val program = new DynAgentsModule(Drone.liftA[Orig], Machines.liftA[Orig])
  val freeap  = program.act(world)
~~~~~~~~

In Chapter 6, we studied the `Const` data type, which allows us to analyse a
program. It should not be surprising that `FreeAp.analyze` is implemented in
terms of `Const`:

{lang="text"}
~~~~~~~~
  sealed abstract class FreeAp[S[_], A] {
    ...
    def analyze[M: Monoid](f: S ~> λ[α => M]): M =
      foldMap(λ[S ~> Const[M, ?]](x => Const(f(x)))).getConst
  }
~~~~~~~~

We provide a natural transformation to record all node starts and `.analyze` our
program to get all the nodes that need to be started:

{lang="text"}
~~~~~~~~
  val gather = λ[Orig ~> λ[α => IList[MachineNode]]] {
    case Coproduct(-\/(Machines.Start(node))) => IList.single(node)
    case _                                    => IList.empty
  }
  val gathered: IList[MachineNode] = freeap.analyze(gather)
~~~~~~~~

The next step is to extend the instruction set from `Orig` to `Extended`, which
includes the `Batch.Ast` and write a `FreeAp` program that starts all our
`gathered` nodes in a single network call

{lang="text"}
~~~~~~~~
  type Extended[a] = Coproduct[Batch.Ast, Orig, a]
  def batch(nodes: IList[MachineNode]): FreeAp[Extended, Unit] =
    nodes.toNel match {
      case None        => FreeAp.pure(())
      case Some(nodes) => FreeAp.lift(Coproduct.leftc(Batch.Start(nodes)))
    }
~~~~~~~~

We also need to remove all the calls to `Machines.Start`, which we can do with a natural transformation

{lang="text"}
~~~~~~~~
  val nostart = λ[Orig ~> FreeAp[Extended, ?]] {
    case Coproduct(-\/(Machines.Start(_))) => FreeAp.pure(())
    case other                             => FreeAp.lift(Coproduct.rightc(other))
  }
~~~~~~~~

Now we have two programs, and need to combine them. Recall the `*>` syntax from
`Apply`

{lang="text"}
~~~~~~~~
  val patched = batch(gathered) *> freeap.foldMap(nostart)
~~~~~~~~

Putting it all together under a single method:

{lang="text"}
~~~~~~~~
  def optimise[A](orig: FreeAp[Orig, A]): FreeAp[Extended, A] =
    (batch(orig.analyze(gather)) *> orig.foldMap(nostart))
~~~~~~~~

That Is it! We `.optimise` every time we call `act` in our main loop, which is
just a matter of plumbing.


### `Coyoneda` (`Functor`)

Named after mathematician Nobuo Yoneda, we can freely generate a `Functor` data
structure for any algebra `S[_]`

{lang="text"}
~~~~~~~~
  sealed abstract class Coyoneda[S[_], A] {
    def run(implicit S: Functor[S]): S[A] = ...
    def trans[G[_]](f: F ~> G): Coyoneda[G, A] = ...
    ...
  }
  object Coyoneda {
    implicit def functor[S[_], A]: Functor[Coyoneda[S, A]] = ...
  
    private final case class Map[F[_], A, B](fa: F[A], f: A => B) extends Coyoneda[F, A]
    def apply[S[_], A, B](sa: S[A])(f: A => B) = Map[S, A, B](sa, f)
    def lift[S[_], A](sa: S[A]) = Map[S, A, A](sa, identity)
    ...
  }
~~~~~~~~

and there is also a contravariant version

{lang="text"}
~~~~~~~~
  sealed abstract class ContravariantCoyoneda[S[_], A] {
    def run(implicit S: Contravariant[S]): S[A] = ...
    def trans[G[_]](f: F ~> G): ContravariantCoyoneda[G, A] = ...
    ...
  }
  object ContravariantCoyoneda {
    implicit def contravariant[S[_], A]: Contravariant[ContravariantCoyoneda[S, A]] = ...
  
    private final case class Contramap[F[_], A, B](fa: F[A], f: B => A)
      extends ContravariantCoyoneda[F, A]
    def apply[S[_], A, B](sa: S[A])(f: B => A) = Contramap[S, A, B](sa, f)
    def lift[S[_], A](sa: S[A]) = Contramap[S, A, A](sa, identity)
    ...
  }
~~~~~~~~

A> The colloquial for `Coyoneda` is *coyo* and `ContravariantCoyoneda` is *cocoyo*.
A> Just some Free Fun.

The API is somewhat simpler than `Free` and `FreeAp`, allowing a natural
transformation with `.trans` and a `.run` (taking an actual `Functor` or
`Contravariant`, respectively) to escape the free structure.

Coyo and cocoyo can be a useful utility if we want to `.map` or `.contramap`
over a type, and we know that we can convert into a data type that has a Functor
but we don't want to commit to the final data structure too early. For example,
we create a `Coyoneda[ISet, ?]` (recall `ISet` does not have a `Functor`) to use
methods that require a `Functor`, then convert into `IList` later on.

If we want to optimise a program with coyo or cocoyo we have to provide the
expected boilerplate for each algebra:

{lang="text"}
~~~~~~~~
  def liftCoyo[F[_]](implicit I: Ast :<: F) = new Machines[Coyoneda[F, ?]] {
    def getTime = Coyoneda.lift(I.inj(GetTime()))
    ...
  }
  def liftCocoyo[F[_]](implicit I: Ast :<: F) = new Machines[ContravariantCoyoneda[F, ?]] {
    def getTime = ContravariantCoyoneda.lift(I.inj(GetTime()))
    ...
  }
~~~~~~~~

An optimisation we get by using `Coyoneda` is *map fusion* (and *contramap
fusion*), which allows us to rewrite

{lang="text"}
~~~~~~~~
  xs.map(a).map(b).map(c)
~~~~~~~~

into

{lang="text"}
~~~~~~~~
  xs.map(x => c(b(a(x))))
~~~~~~~~

avoiding intermediate representations. For example, if `xs` is a `List` of a
thousand elements, we save two thousand object allocations because we only map
over the data structure once.

However it is arguably a lot easier to just make this kind of change in the
original function by hand, or to wait for the [`scalaz-plugin`](https://github.com/scalaz/scalaz-plugin) project to be
released and automatically perform these sorts of optimisations.


### Extensible Effects

Programs are just data: free structures help to make this explicit and give us
the ability to rearrange and optimise that data.

`Free` is more special than it appears: it can sequence arbitrary algebras and
typeclasses.

For example, a free structure for `MonadState` is available. The `Ast` and
`.liftF` are more complicated than usual because we have to account for the `S`
type parameter on `MonadState`, and the inheritance from `Monad`:

{lang="text"}
~~~~~~~~
  object MonadState {
    sealed abstract class Ast[S, A]
    final case class Get[S]()     extends Ast[S, S]
    final case class Put[S](s: S) extends Ast[S, Unit]
  
    def liftF[F[_], S](implicit I: Ast[S, ?] :<: F) =
      new MonadState[Free[F, ?], S] with BindRec[Free[F, ?]] {
        def get       = Free.liftF(I.inj(Get[S]()))
        def put(s: S) = Free.liftF(I.inj(Put[S](s)))
  
        val delegate         = Free.freeMonad[F]
        def point[A](a: =>A) = delegate.point(a)
        ...
      }
    ...
  }
~~~~~~~~

This gives us the opportunity to use optimised interpreters. For example, we
could store the `S` in an atomic field instead of building up a nested `StateT`
trampoline.

We can create an `Ast` and `.liftF` for almost any algebra or typeclass! The
only restriction is that the `F[_]` does not appear as a parameter to any of the
instructions, i.e. it must be possible for the algebra to have an instance of
`Functor`. This unfortunately rules out `MonadError` and `Monoid`.

A> The reason why free encodings do not work for all algebras and typeclasses is
A> quite subtle.
A> 
A> Consider what happens if we create an Ast for `MonadError`, with `F[_]` in
A> contravariant position, i.e. as a parameter.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   object MonadError {
A>     sealed abstract class Ast[F[_], E, A]
A>     final case class RaiseError[F[_], E, A](e: E) extends Ast[F, E, A]
A>     final case class HandleError[F[_], E, A](fa: F[A], f: E => F[A]) extends Ast[F, E, A]
A>   
A>     def liftF[F[_], E](implicit I: Ast[F, E, ?] :<: F): MonadError[F, E] = ...
A>     ...
A>   }
A> ~~~~~~~~
A> 
A> When we come to interpret a program that uses `MonadError.Ast` we must construct
A> the coproduct of instructions. Say we extend a `Drone` program:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   type Ast[a] = Coproduct[MonadError.Ast[Ast, String, ?], Drone.Ast, a]
A> ~~~~~~~~
A> 
A> This fails to compile because `Ast` refers to itself!
A> 
A> Algebras that are not entirely made of covariant functor signatures, i.e. `F[_]`
A> in return position, are impossible to interpret because the resulting type of
A> the program is self-referential. Indeed the name *algebra* that we have been
A> using has its roots in [F-Algebras](https://en.wikipedia.org/wiki/F-algebra), where the F is for Functor.
A> 
A> *Thanks to Edmund Noble for initiating this discussion.*

As the AST of a free program grows, performance degrades because the interpreter
must match over instruction sets with an `O(n)` cost. An alternative to
`scalaz.Coproduct` is [iotaz](https://github.com/frees-io/iota)'s encoding, which uses an optimised data structure
to perform `O(1)` dynamic dispatch (using integers that are assigned to each
coproduct at compiletime).

For historical reasons a free AST for an algebra or typeclass is called *Initial
Encoding*, and a direct implementation (e.g. with `IO`) is called *Finally
Tagless*. Although we have explored interesting ideas with `Free`, it is
generally accepted that finally tagless is superior. But to use finally tagless
style, we need a high performance effect type that provides all the monad
typeclasses we've covered in this chapter. We also still need to be able to run
our `Applicative` code in parallel. This is exactly what we will cover next.


## `Parallel`

There are two effectful operations that we almost always want to run in
parallel:

1.  `.map` over a collection of effects, returning a single effect. This is
    achieved by `.traverse`, which delegates to the effect's `.apply2`.
2.  running a fixed number of effects with the *scream operator* `|@|`, and
    combining their output, again delegating to `.apply2`.

However, in practice, neither of these operations execute in parallel by
default. The reason is that if our `F[_]` is implemented by a `Monad`, then the
derived combinator laws for `.apply2` must be satisfied, which say

{lang="text"}
~~~~~~~~
  @typeclass trait Bind[F[_]] extends Apply[F] {
    ...
    override def apply2[A, B, C](fa: =>F[A], fb: =>F[B])(f: (A, B) => C): F[C] =
      bind(fa)(a => map(fb)(b => f(a, b)))
    ...
  }
~~~~~~~~

In other words, **`Monad` is explicitly forbidden from running effects in
parallel.**

However, if we have an `F[_]` that is **not** monadic, then it may implement
`.apply2` in parallel. We can use the `@@` (tag) mechanism to create an instance
of `Applicative` for `F[_] @@ Parallel`, which is conveniently assigned to the
type alias `Applicative.Par`

{lang="text"}
~~~~~~~~
  object Applicative {
    type Par[F[_]] = Applicative[λ[α => F[α] @@ Tags.Parallel]]
    ...
  }
~~~~~~~~

Monadic programs can then request an implicit `Par` in addition to their `Monad`

{lang="text"}
~~~~~~~~
  def foo[F[_]: Monad: Applicative.Par]: F[Unit] = ...
~~~~~~~~

Scalaz's `Traverse` syntax supports parallelism:

{lang="text"}
~~~~~~~~
  implicit class TraverseSyntax[F[_], A](self: F[A]) {
    ...
    def parTraverse[G[_], B](f: A => G[B])(
      implicit F: Traverse[F], G: Applicative.Par[G]
    ): G[F[B]] = Tag.unwrap(F.traverse(self)(a => Tag(f(a))))
  }
~~~~~~~~

If the implicit `Applicative.Par[IO]` is in scope, we can choose between
sequential and parallel traversal:

{lang="text"}
~~~~~~~~
  val input: IList[String] = ...
  def network(in: String): IO[Int] = ...
  
  input.traverse(network): IO[IList[Int]] // one at a time
  input.parTraverse(network): IO[IList[Int]] // all in parallel
~~~~~~~~

Similarly, we can call `.parApply` or `.parTupled` after using scream operators

{lang="text"}
~~~~~~~~
  val fa: IO[String] = ...
  val fb: IO[String] = ...
  val fc: IO[String] = ...
  
  (fa |@| fb).parTupled: IO[(String, String)]
  
  (fa |@| fb |@| fc).parApply { case (a, b, c) => a + b + c }: IO[String]
~~~~~~~~

It is worth nothing that when we have `Applicative` programs, such as

{lang="text"}
~~~~~~~~
  def foo[F[_]: Applicative]: F[Unit] = ...
~~~~~~~~

we can use `F[A] @@ Parallel` as our program's context and get parallelism as
the default on `.traverse` and `|@|`. Converting between the raw and `@@
Parallel` versions of `F[_]` must be handled manually in the glue code, which
can be painful. Therefore it is often easier to simply request both forms of
`Applicative`

{lang="text"}
~~~~~~~~
  def foo[F[_]: Applicative: Applicative.Par]: F[Unit] = ...
~~~~~~~~


### Breaking the Law

We can take a more daring approach to parallelism: opt-out of the law that
`.apply2` must be sequential for `Monad`. This is highly controversial, but
works well for the majority of real world applications. we must first audit our
codebase (including third party dependencies) to ensure that nothing is making
use of the `.apply2` implied law.

We wrap `IO`

{lang="text"}
~~~~~~~~
  final class MyIO[A](val io: IO[A]) extends AnyVal
~~~~~~~~

and provide our own implementation of `Monad` which runs `.apply2` in parallel
by delegating to a `@@ Parallel` instance

{lang="text"}
~~~~~~~~
  object MyIO {
    implicit val monad: Monad[MyIO] = new Monad[MyIO] {
      override def apply2[A, B, C](fa: MyIO[A], fb: MyIO[B])(f: (A, B) => C): MyIO[C] =
        Applicative[IO.Par].apply2(fa.io, fb.io)(f)
      ...
    }
  }
~~~~~~~~

We can now use `MyIO` as our application's context instead of `IO`, and **get
parallelism by default**.

A> Wrapping an existing type and providing custom typeclass instances is known as
A> *newtyping*.
A> 
A> `@@` and newtyping are complementary: `@@` allows us to request specific
A> typeclass variants on our domain model, whereas newtyping allow us to define the
A> instances on the implementation. Same thing, different insertion points.
A> 
A> The `@newtype` macro [by Cary Robbins](https://github.com/estatico/scala-newtype) has an optimised runtime representation
A> (more efficient than `extends AnyVal`), that makes it easy to delegate
A> typeclasses that we do not wish to customise. For example, we can customise
A> `Monad` but delegate the `Plus`:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   @newtype class MyIO[A](io: IO[A])
A>   object MyIO {
A>     implicit val monad: Monad[MyIO] = ...
A>     implicit val plus: Plus[MyIO] = derived
A>   }
A> ~~~~~~~~

For completeness: a naive and inefficient implementation of `Applicative.Par`
for our toy `IO` could use `Future`:

{lang="text"}
~~~~~~~~
  object IO {
    ...
    type Par[a] = IO[a] @@ Parallel
    implicit val ParApplicative = new Applicative[Par] {
      override def apply2[A, B, C](fa: =>Par[A], fb: =>Par[B])(f: (A, B) => C): Par[C] =
        Tag(
          IO {
            val forked = Future { Tag.unwrap(fa).interpret() }
            val b      = Tag.unwrap(fb).interpret()
            val a      = Await.result(forked, Duration.Inf)
            f(a, b)
          }
        )
  }
~~~~~~~~

and due to [a bug in the Scala compiler](https://github.com/scala/bug/issues/10954) that treats all `@@` instances as
orphans, we must explicitly import the implicit:

{lang="text"}
~~~~~~~~
  import IO.ParApplicative
~~~~~~~~

In the final section of this chapter we will see how Scalaz's `IO` is actually
implemented.


## `IO`

Scalaz's `IO` is the fastest asynchronous programming construct in the Scala
ecosystem: up to 50 times faster than `Future`. `IO` is a free data structure
specialised for use as a general effect monad.

{lang="text"}
~~~~~~~~
  sealed abstract class IO[E, A] { ... }
  object IO {
    private final class FlatMap         ... extends IO[E, A]
    private final class Point           ... extends IO[E, A]
    private final class Strict          ... extends IO[E, A]
    private final class SyncEffect      ... extends IO[E, A]
    private final class Fail            ... extends IO[E, A]
    private final class AsyncEffect     ... extends IO[E, A]
    ...
  }
~~~~~~~~

`IO` has **two** type parameters: it has a `Bifunctor` allowing the error type to
be an application specific ADT. But because we are on the JVM, and must interact
with legacy libraries, a convenient type alias is provided that uses exceptions
for the error type:

{lang="text"}
~~~~~~~~
  type Task[A] = IO[Throwable, A]
~~~~~~~~

A> `scalaz.ioeffect.IO` is a high performance `IO` by John de Goes. It has a
A> separate lifecycle to the core Scalaz library and must be manually added to our
A> `build.sbt` with
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   libraryDependencies += "org.scalaz" %% "scalaz-ioeffect" % "2.10.1"
A> ~~~~~~~~
A> 
A> Do not use the deprecated `scalaz-effect` and `scalaz-concurrency` packages.
A> 
A> Prefer the `scalaz.ioeffect` variants of all typeclasses and data types.


### Creating

There are multiple ways to create an `IO` that cover a variety of eager, lazy,
safe and unsafe code blocks:

{lang="text"}
~~~~~~~~
  object IO {
    // eager evaluation of an existing value
    def now[E, A](a: A): IO[E, A] = ...
    // lazy evaluation of a pure calculation
    def point[E, A](a: =>A): IO[E, A] = ...
    // lazy evaluation of a side-effecting, yet Total, code block
    def sync[E, A](effect: =>A): IO[E, A] = ...
    // lazy evaluation of a side-effecting code block that may fail
    def syncThrowable[A](effect: =>A): IO[Throwable, A] = ...
  
    // create a failed IO
    def fail[E, A](error: E): IO[E, A] = ...
    // asynchronously sleeps for a specific period of time
    def sleep[E](duration: Duration): IO[E, Unit] = ...
    ...
  }
~~~~~~~~

with convenient `Task` constructors:

{lang="text"}
~~~~~~~~
  object Task {
    def apply[A](effect: =>A): Task[A] = IO.syncThrowable(effect)
    def now[A](effect: A): Task[A] = IO.now(effect)
    def fail[A](error: Throwable): Task[A] = IO.fail(error)
    def fromFuture[E, A](io: Task[Future[A]])(ec: ExecutionContext): Task[A] = ...
  }
~~~~~~~~

The most common constructors, by far, when dealing with legacy code are
`Task.apply` and `Task.fromFuture`:

{lang="text"}
~~~~~~~~
  val fa: Task[Future[String]] = Task { ... impure code here ... }
  
  Task.fromFuture(fa)(ExecutionContext.global): Task[String]
~~~~~~~~

We cannot pass around raw `Future`, because it eagerly evaluates, so must always
be constructed inside a safe block.

Note that the `ExecutionContext` is **not** `implicit`, contrary to the
convention. Recall that in Scalaz we reserve the `implicit` keyword for
typeclass derivation, to simplify the language: `ExecutionContext` is
configuration that must be provided explicitly.


### Running

The `IO` interpreter is called `RTS`, for *runtime system*. Its implementation
is beyond the scope of this book. We will instead focus on the features that
`IO` provides.

`IO` is just a data structure, and is interpreted *at the end of the world* by
extending `SafeApp` and implementing `.run`

{lang="text"}
~~~~~~~~
  trait SafeApp extends RTS {
  
    sealed trait ExitStatus
    object ExitStatus {
      case class ExitNow(code: Int)                         extends ExitStatus
      case class ExitWhenDone(code: Int, timeout: Duration) extends ExitStatus
      case object DoNotExit                                 extends ExitStatus
    }
  
    def run(args: List[String]): IO[Void, ExitStatus]
  
    final def main(args0: Array[String]): Unit = ... calls run ...
  }
~~~~~~~~

A> `Void` is a type that has no values, like `scala.Nothing`. However, the Scala
A> compiler infers `Nothing` when it fails to correctly infer a type parameter,
A> causing confusing error messages, whereas `Void` will fail fast during
A> compilation.
A> 
A> A `Void` error type means that the effect **cannot fail**, which is to say that we
A> have handled all errors by this point.

If we are integrating with a legacy system and are not in control of the entry
point of our application, we can extend the `RTS` and gain access to unsafe
methods to evaluate the `IO` at the entry point to our principled FP code.


### Features

`IO` provides typeclass instances for `Bifunctor`, `MonadError[E, ?]`,
`BindRec`, `Plus`, `MonadPlus` (if `E` forms a `Monoid`), and an
`Applicative[IO.Par[E, ?]]`.

In addition to the functionality from the typeclasses, there are implementation
specific methods:

{lang="text"}
~~~~~~~~
  sealed abstract class IO[E, A] {
    // retries an action N times, until success
    def retryN(n: Int): IO[E, A] = ...
    // ... with exponential backoff
    def retryBackoff(n: Int, factor: Double, duration: Duration): IO[E, A] = ...
  
    // repeats an action with a pause between invocations, until it fails
    def repeat[B](interval: Duration): IO[E, B] = ...
  
    // cancel the action if it does not complete within the timeframe
    def timeout(duration: Duration): IO[E, Maybe[A]] = ...
  
    // runs `release` on success or failure.
    // Note that IO[Void, Unit] cannot fail.
    def bracket[B](release: A => IO[Void, Unit])(use: A => IO[E, B]): IO[E, B] = ...
    // alternative syntax for bracket
    def ensuring(finalizer: IO[Void, Unit]): IO[E, A] =
    // ignore failure and success, e.g. to ignore the result of a cleanup action
    def ignore: IO[Void, Unit] = ...
  
    // runs two effects in parallel
    def par[B](that: IO[E, B]): IO[E, (A, B)] = ...
    ...
~~~~~~~~

It is possible for an `IO` to be in a *terminated* state, which represents work
that is intended to be discarded (it is neither an error nor a success). The
utilities related to termination are:

{lang="text"}
~~~~~~~~
  ...
    // terminate whatever actions are running with the given throwable.
    // bracket / ensuring is honoured.
    def terminate[E, A](t: Throwable): IO[E, A] = ...
  
    // runs two effects in parallel, return the winner and terminate the loser
    def race(that: IO[E, A]): IO[E, A] = ...
  
    // ignores terminations
    def uninterruptibly: IO[E, A] = ...
  ...
~~~~~~~~


### `Fiber`

An `IO` may spawn *fibers*, a lightweight abstraction over a JVM `Thread`. We
can `.fork` an `IO`, and `.supervise` any incomplete fibers to ensure that they
are terminated when the `IO` action completes

{lang="text"}
~~~~~~~~
  ...
    def fork[E2]: IO[E2, Fiber[E, A]] = ...
    def supervised(error: Throwable): IO[E, A] = ...
  ...
~~~~~~~~

When we have a `Fiber` we can `.join` back into the `IO`, or `interrupt` the
underlying work.

{lang="text"}
~~~~~~~~
  trait Fiber[E, A] {
    def join: IO[E, A]
    def interrupt[E2](t: Throwable): IO[E2, Unit]
  }
~~~~~~~~

We can use fibers to achieve a form of optimistic concurrency control. Consider
the case where we have `data` that we need to analyse, but we also need to
validate it. We can optimistically begin the analysis and cancel the work if the
validation fails, which is performed in parallel.

{lang="text"}
~~~~~~~~
  final class BadData(data: Data) extends Throwable with NoStackTrace
  
  for {
    fiber1   <- analysis(data).fork
    fiber2   <- validate(data).fork
    valid    <- fiber2.join
    _        <- if (!valid) fiber1.interrupt(BadData(data))
                else IO.unit
    result   <- fiber1.join
  } yield result
~~~~~~~~

Another usecase for fibers is when we need to perform a *fire and forget*
action. For example, low priority logging over a network.


### `Promise`

A promise represents an asynchronous variable that can be set exactly once (with
`complete` or `error`). An unbounded number of listeners can `get` the variable.

{lang="text"}
~~~~~~~~
  final class Promise[E, A] private (ref: AtomicReference[State[E, A]]) {
    def complete[E2](a: A): IO[E2, Boolean] = ...
    def error[E2](e: E): IO[E2, Boolean] = ...
    def get: IO[E, A] = ...
  
    // interrupts all listeners
    def interrupt[E2](t: Throwable): IO[E2, Boolean] = ...
  }
  object Promise {
    def make[E, A]: IO[E, Promise[E, A]] = ...
  }
~~~~~~~~

`Promise` is not something that we typically use in application code. It is a
building block for high level concurrency frameworks.

A> When an operation is guaranteed to succeed, the error type `E` is left as a free
A> type parameter so that the caller can specify their preference.


### `IORef`

`IORef` is the `IO` equivalent of an atomic mutable variable.

We can read the variable and we have a variety of ways to write or update it.

{lang="text"}
~~~~~~~~
  final class IORef[A] private (ref: AtomicReference[A]) {
    def read[E]: IO[E, A] = ...
  
    // write with immediate consistency guarantees
    def write[E](a: A): IO[E, Unit] = ...
    // write with eventual consistency guarantees
    def writeLater[E](a: A): IO[E, Unit] = ...
    // return true if an immediate write succeeded, false if not (and abort)
    def tryWrite[E](a: A): IO[E, Boolean] = ...
  
    // atomic primitives for updating the value
    def compareAndSet[E](prev: A, next: A): IO[E, Boolean] = ...
    def modify[E](f: A => A): IO[E, A] = ...
    def modifyFold[E, B](f: A => (B, A)): IO[E, B] = ...
  }
  object IORef {
    def apply[E, A](a: A): IO[E, IORef[A]] = ...
  }
~~~~~~~~

`IORef` is another building block and can be used to provide a high performance
`MonadState`. For example, create a newtype specialised to `Task`

{lang="text"}
~~~~~~~~
  final class StateTask[A](val io: Task[A]) extends AnyVal
  object StateTask {
    def create[S](initial: S): Task[MonadState[StateTask, S]] =
      for {
        ref <- IORef(initial)
      } yield
        new MonadState[StateTask, S] {
          override def get       = new StateTask(ref.read)
          override def put(s: S) = new StateTask(ref.write(s))
          ...
        }
  }
~~~~~~~~

We can make use of this optimised `StateMonad` implementation in a `SafeApp`,
where our `.program` depends on optimised MTL typeclasses:

{lang="text"}
~~~~~~~~
  object FastState extends SafeApp {
    def program[F[_]](implicit F: MonadState[F, Int]): F[ExitStatus] = ...
  
    def run(@unused args: List[String]): IO[Void, ExitStatus] =
      for {
        stateMonad <- StateTask.create(10)
        output     <- program(stateMonad).io
      } yield output
  }
~~~~~~~~

A more realistic application would take a variety of algebras and typeclasses as
input.

A> This optimised `MonadState` is constructed in a way that breaks typeclass
A> coherence. Two instances having the same types may be managing different state.
A> It would be prudent to isolate the construction of all such instances to the
A> application's entrypoint.


#### `MonadIO`

The `MonadIO` that we previously studied was simplified to hide the `E`
parameter. The actual typeclass is

{lang="text"}
~~~~~~~~
  trait MonadIO[M[_], E] {
    def liftIO[A](io: IO[E, A])(implicit M: Monad[M]): M[A]
  }
~~~~~~~~

with a minor change to the boilerplate on the companion of our algebra,
accounting for the extra `E`:

{lang="text"}
~~~~~~~~
  trait Lookup[F[_]] {
    def look: F[Int]
  }
  object Lookup {
    def liftIO[F[_]: Monad, E](io: Lookup[IO[E, ?]])(implicit M: MonadIO[F, E]) =
      new Lookup[F] {
        def look: F[Int] = M.liftIO(io.look)
      }
    ...
  }
~~~~~~~~


## Summary

1.  The `Future` is broke, don't go there.
2.  Manage stack safety with a `Trampoline`.
3.  The Monad Transformer Library (MTL) abstracts over common effects with typeclasses.
4.  Monad Transformers provide default implementations of the MTL.
5.  `Free` data structures let us analyse, optimise and easily test our programs.
6.  `IO` gives us the ability to implement algebras as effects on the world.
7.  `IO` can perform effects in parallel and is a high performance backbone for any application.


# Typeclass Derivation

Typeclasses provide polymorphic functionality to our applications. But to use a
typeclass we need instances for our business domain objects.

The creation of a typeclass instance from existing instances is known as
*typeclass derivation* and is the topic of this chapter.

There are four approaches to typeclass derivation:

1.  Manual instances for every domain object. This is infeasible for real world
    applications as it results in hundreds of lines of boilerplate for every line
    of a `case class`. It is useful only for educational purposes and adhoc
    performance optimisations.

2.  Abstract over the typeclass by an existing Scalaz typeclass. This is the
    approach of `scalaz-deriving`, producing automated tests and derivations for
    products and coproducts

3.  Macros. However, writing a macro for each typeclass requires an advanced and
    experienced developer. Fortunately, Jon Pretty's [Magnolia](https://github.com/propensive/magnolia) library abstracts
    over hand-rolled macros with a simple API, centralising the complex
    interaction with the compiler.

4.  Write a generic program using the [Shapeless](https://github.com/milessabin/shapeless/) library. The `implicit` mechanism
    is a language within the Scala language and can be used to write programs at
    the type level.

In this chapter we will study increasingly complex typeclasses and their
derivations. We will begin with `scalaz-deriving` as the most principled
mechanism, repeating some lessons from Chapter 5 "Scalaz Typeclasses", then
Magnolia (the easiest to use), finishing with Shapeless (the most powerful) for
typeclasses with complex derivation logic.


## Running Examples

This chapter will show how to define derivations for five specific typeclasses.
Each example exhibits a feature that can be generalised:

{lang="text"}
~~~~~~~~
  @typeclass trait Equal[A]  {
    // type parameter is in contravariant (parameter) position
    @op("===") def equal(a1: A, a2: A): Boolean
  }
  
  // for requesting default values of a type when testing
  @typeclass trait Default[A] {
    // type parameter is in covariant (return) position
    def default: String \/ A
  }
  
  @typeclass trait Semigroup[A] {
    // type parameter is in both covariant and contravariant position (invariant)
    @op("|+|") def append(x: A, y: =>A): A
  }
  
  @typeclass trait JsEncoder[T] {
    // type parameter is in contravariant position and needs access to field names
    def toJson(t: T): JsValue
  }
  
  @typeclass trait JsDecoder[T] {
    // type parameter is in covariant position and needs access to field names
    def fromJson(j: JsValue): String \/ T
  }
~~~~~~~~

A> There is a school of thought that says serialisation formats, such as JSON and
A> XML, should **not** have typeclass encoders and decoders, because it can lead to
A> typeclass decoherence (i.e. more than one encoder or decoder may exist for the
A> same type). The alternative is to use algebras and avoid using the `implicit`
A> language feature entirely.
A> 
A> Although it is possible to apply the techniques in this chapter to either
A> typeclass or algebra derivation, the latter involves a **lot** more boilerplate.
A> We therefore consciously choose to restrict our study to encoders and decoders
A> that are coherent. As we will see later in this chapter, use-site automatic
A> derivation with Magnolia and Shapeless, combined with limitations of the Scala
A> compiler's implicit search, commonly leads to typeclass decoherence.


## `scalaz-deriving`

The `scalaz-deriving` library is an extension to Scalaz and can be added to a
project's `build.sbt` with

{lang="text"}
~~~~~~~~
  val derivingVersion = "1.0.0"
  libraryDependencies += "org.scalaz" %% "scalaz-deriving" % derivingVersion
~~~~~~~~

providing new typeclasses, shown below in relation to core Scalaz typeclasses:

{width=60%}
![](images/scalaz-deriving-base.png)

A> In Scalaz 7.3, `Applicative` and `Divisible` will inherit from `InvariantApplicative`

Before we proceed, here is a quick recap of the core Scalaz typeclasses:

{lang="text"}
~~~~~~~~
  @typeclass trait InvariantFunctor[F[_]] {
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B]
  }
  
  @typeclass trait Contravariant[F[_]] extends InvariantFunctor[F] {
    def contramap[A, B](fa: F[A])(f: B => A): F[B]
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B] = contramap(fa)(g)
  }
  
  @typeclass trait Divisible[F[_]] extends Contravariant[F] {
    def conquer[A]: F[A]
    def divide2[A, B, C](fa: F[A], fb: F[B])(f: C => (A, B)): F[C]
    ...
    def divide22[...] = ...
  }
  
  @typeclass trait Functor[F[_]] extends InvariantFunctor[F] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B] = map(fa)(f)
  }
  
  @typeclass trait Applicative[F[_]] extends Functor[F] {
    def point[A](a: =>A): F[A]
    def apply2[A,B,C](fa: =>F[A], fb: =>F[B])(f: (A, B) => C): F[C] = ...
    ...
    def apply12[...]
  }
  
  @typeclass trait Monad[F[_]] extends Functor[F] {
    @op(">>=") def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
  }
  @typeclass trait MonadError[F[_], E] extends Monad[F] {
    def raiseError[A](e: E): F[A]
    def emap[A, B](fa: F[A])(f: A => S \/ B): F[B] = ...
    ...
  }
~~~~~~~~


### Don't Repeat Yourself

The simplest way to derive a typeclass is to reuse one that already exists.

The `Equal` typeclass has an instance of `Contravariant[Equal]`, providing
`.contramap`:

{lang="text"}
~~~~~~~~
  object Equal {
    implicit val contravariant = new Contravariant[Equal] {
      def contramap[A, B](fa: Equal[A])(f: B => A): Equal[B] =
        (b1, b2) => fa.equal(f(b1), f(b2))
    }
    ...
  }
~~~~~~~~

As users of `Equal`, we can use `.contramap` for our single parameter data
types. Recall that typeclass instances go on the data type companions to be in
their implicit scope:

{lang="text"}
~~~~~~~~
  final case class Foo(s: String)
  object Foo {
    implicit val equal: Equal[Foo] = Equal[String].contramap(_.s)
  }
  
  scala> Foo("hello") === Foo("world")
  false
~~~~~~~~

However, not all typeclasses can have an instance of `Contravariant`. In
particular, typeclasses with type parameters in covariant position may have a
`Functor` instead:

{lang="text"}
~~~~~~~~
  object Default {
    def instance[A](d: =>String \/ A) = new Default[A] { def default = d }
    implicit val string: Default[String] = instance("".right)
  
    implicit val functor: Functor[Default] = new Functor[Default] {
      def map[A, B](fa: Default[A])(f: A => B): Default[B] = instance(fa.default.map(f))
    }
    ...
  }
~~~~~~~~

We can now derive a `Default[Foo]`

{lang="text"}
~~~~~~~~
  object Foo {
    implicit val default: Default[Foo] = Default[String].map(Foo(_))
    ...
  }
~~~~~~~~

If a typeclass has parameters in both covariant and contravariant position, as
is the case with `Semigroup`, it may provide an `InvariantFunctor`

{lang="text"}
~~~~~~~~
  object Semigroup {
    implicit val invariant = new InvariantFunctor[Semigroup] {
      def xmap[A, B](ma: Semigroup[A], f: A => B, g: B => A) = new Semigroup[B] {
        def append(x: B, y: =>B): B = f(ma.append(g(x), g(y)))
      }
    }
    ...
  }
~~~~~~~~

and we can call `.xmap`

{lang="text"}
~~~~~~~~
  object Foo {
    implicit val semigroup: Semigroup[Foo] = Semigroup[String].xmap(Foo(_), _.s)
    ...
  }
~~~~~~~~

Generally, it is simpler to just use `.xmap` instead of `.map` or `.contramap`:

{lang="text"}
~~~~~~~~
  final case class Foo(s: String)
  object Foo {
    implicit val equal: Equal[Foo]         = Equal[String].xmap(Foo(_), _.s)
    implicit val default: Default[Foo]     = Default[String].xmap(Foo(_), _.s)
    implicit val semigroup: Semigroup[Foo] = Semigroup[String].xmap(Foo(_), _.s)
  }
~~~~~~~~

A> The `@xderiving` annotation automatically inserts `.xmap` boilerplate. Add the
A> following to `build.sbt`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   addCompilerPlugin("org.scalaz" %% "deriving-plugin" % derivingVersion)
A>   libraryDependencies += "org.scalaz" %% "deriving-macro" % derivingVersion % "provided"
A> ~~~~~~~~
A> 
A> and use it as
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   @xderiving(Equal, Default, Semigroup)
A>   final case class Foo(s: String)
A> ~~~~~~~~


### `MonadError`

Typically things that *write* from a polymorphic value have a `Contravariant`,
and things that *read* into a polymorphic value have a `Functor`. However, it is
very much expected that reading can fail. For example, if we have a default
`String` it does not mean that we can simply derive a default `String Refined
NonEmpty` from it

{lang="text"}
~~~~~~~~
  import eu.timepit.refined.refineV
  import eu.timepit.refined.api._
  import eu.timepit.refined.collection._
  
  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].map(refineV[NonEmpty](_))
~~~~~~~~

fails to compile with

{lang="text"}
~~~~~~~~
  [error] default.scala:41:32: polymorphic expression cannot be instantiated to expected type;
  [error]  found   : Either[String, String Refined NonEmpty]
  [error]  required: String Refined NonEmpty
  [error]     Default[String].map(refineV[NonEmpty](_))
  [error]                                          ^
~~~~~~~~

Recall from Chapter 4.1 that `refineV` returns an `Either`, as the compiler has
reminded us.

As the typeclass author of `Default`, we can do better than `Functor` and
provide a `MonadError[Default, String]`:

{lang="text"}
~~~~~~~~
  implicit val monad = new MonadError[Default, String] {
    def point[A](a: =>A): Default[A] =
      instance(a.right)
    def bind[A, B](fa: Default[A])(f: A => Default[B]): Default[B] =
      instance((fa >>= f).default)
    def handleError[A](fa: Default[A])(f: String => Default[A]): Default[A] =
      instance(fa.default.handleError(e => f(e).default))
    def raiseError[A](e: String): Default[A] =
      instance(e.left)
  }
~~~~~~~~

Now we have access to `.emap` syntax and can derive our refined type

{lang="text"}
~~~~~~~~
  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].emap(refineV[NonEmpty](_).disjunction)
~~~~~~~~

In fact, we can provide a derivation rule for all refined types

{lang="text"}
~~~~~~~~
  implicit def refined[A: Default, P](
    implicit V: Validate[A, P]
  ): Default[A Refined P] = Default[A].emap(refineV[P](_).disjunction)
~~~~~~~~

where `Validate` is from the refined library and is required by `refineV`.

A> The `refined-scalaz` extension to `refined` provides support for automatically
A> deriving all typeclasses for refined types with the following import
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   import eu.timepit.refined.scalaz._
A> ~~~~~~~~
A> 
A> if there is a `Contravariant` or `MonadError[?, String]` in the implicit scope.
A> 
A> However, due to [limitations of the Scala compiler](https://github.com/scala/bug/issues/10753) it rarely works in practice
A> and we must write `implicit def refined` derivations for each typeclass.

Similarly we can use `.emap` to derive an `Int` decoder from a `Long`, with
protection around the non-total `.toInt` stdlib method.

{lang="text"}
~~~~~~~~
  implicit val long: Default[Long] = instance(0L.right)
  implicit val int: Default[Int] = Default[Long].emap {
    case n if (Int.MinValue <= n && n <= Int.MaxValue) => n.toInt.right
    case big => s"$big does not fit into 32 bits".left
  }
~~~~~~~~

As authors of the `Default` typeclass, we might want to reconsider our API
design so that it can never fail, e.g. with the following type signature

{lang="text"}
~~~~~~~~
  @typeclass trait Default[A] {
    def default: A
  }
~~~~~~~~

We would not be able to define a `MonadError`, forcing us to provide instances
that always succeed. This will result in more boilerplate but gains compiletime
safety. However, we will continue with `String \/ A` as the return type as it is
a more general example.


### `.fromIso`

All of the typeclasses in Scalaz have a method on their companion with a
signature similar to the following:

{lang="text"}
~~~~~~~~
  object Equal {
    def fromIso[F, G: Equal](D: F <=> G): Equal[F] = ...
    ...
  }
  
  object Monad {
    def fromIso[F[_], G[_]: Monad](D: F <~> G): Monad[F] = ...
    ...
  }
~~~~~~~~

These mean that if we have a type `F`, and a way to convert it into a `G` that
has an instance, we can call `Equal.fromIso` to obtain an instance for `F`.

For example, as typeclass users, if we have a data type `Bar` we can define an
isomorphism to `(String, Int)`

{lang="text"}
~~~~~~~~
  import Isomorphism._
  
  final case class Bar(s: String, i: Int)
  object Bar {
    val iso: Bar <=> (String, Int) = IsoSet(b => (b.s, b.i), t => Bar(t._1, t._2))
  }
~~~~~~~~

and then derive `Equal[Bar]` because there is already an `Equal` for all tuples:

{lang="text"}
~~~~~~~~
  object Bar {
    ...
    implicit val equal: Equal[Bar] = Equal.fromIso(iso)
  }
~~~~~~~~

The `.fromIso` mechanism can also assist us as typeclass authors. Consider
`Default` which has a core type signature of the form `Unit => F[A]`. Our
`default` method is in fact isomorphic to `Kleisli[F, Unit, A]`, the `ReaderT`
monad transformer.

Since `Kleisli` already provides a `MonadError` (if `F` has one), we can derive
`MonadError[Default, String]` by creating an isomorphism between `Default` and
`Kleisli`:

{lang="text"}
~~~~~~~~
  private type Sig[a] = Unit => String \/ a
  private val iso = Kleisli.iso(
    λ[Sig ~> Default](s => instance(s(()))),
    λ[Default ~> Sig](d => _ => d.default)
  )
  implicit val monad: MonadError[Default, String] = MonadError.fromIso(iso)
~~~~~~~~

giving us the `.map`, `.xmap` and `.emap` that we've been making use of so far,
effectively for free.


### `Divisible` and `Applicative`

To derive the `Equal` for our case class with two parameters, we reused the
instance that Scalaz provides for tuples. But where did the tuple instance come
from?

A more specific typeclass than `Contravariant` is `Divisible`. `Equal` has an
instance:

{lang="text"}
~~~~~~~~
  implicit val divisible = new Divisible[Equal] {
    ...
    def divide[A1, A2, Z](a1: =>Equal[A1], a2: =>Equal[A2])(
      f: Z => (A1, A2)
    ): Equal[Z] = { (z1, z2) =>
      val (s1, s2) = f(z1)
      val (t1, t2) = f(z2)
      a1.equal(s1, t1) && a2.equal(s2, t2)
    }
    def conquer[A]: Equal[A] = (_, _) => true
  }
~~~~~~~~

A> When implementing `Divisible` the compiler will require us to provide
A> `.contramap`, which we can do directly with an optimised implementation or with
A> this derived combinator:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   override def contramap[A, B](fa: F[A])(f: B => A): F[B] =
A>     divide2(conquer[Unit], fa)(c => ((), f(c)))
A> ~~~~~~~~
A> 
A> This has been added to `Divisible` in Scalaz 7.3.

And from `divide2`, `Divisible` is able to build up derivations all the way to
`divide22`. We can call these methods directly for our data types:

{lang="text"}
~~~~~~~~
  final case class Bar(s: String, i: Int)
  object Bar {
    implicit val equal: Equal[Bar] =
      Divisible[Equal].divide2(Equal[String], Equal[Int])(b => (b.s, b.i))
  }
~~~~~~~~

The equivalent for type parameters in covariant position is `Applicative`:

{lang="text"}
~~~~~~~~
  object Bar {
    ...
    implicit val default: Default[Bar] =
      Applicative[Default].apply2(Default[String], Default[Int])(Bar(_, _))
  }
~~~~~~~~

But we must be careful that we do not break the typeclass laws when we implement
`Divisible` or `Applicative`. In particular, it is easy to break the *law of
composition* which says that the following two codepaths must yield exactly the
same output

-   `divide2(divide2(a1, a2)(dupe), a3)(dupe)`
-   `divide2(a1, divide2(a2, a3)(dupe))(dupe)`
-   for any `dupe: A => (A, A)`

with similar laws for `Applicative`.

Consider `JsEncoder` and a proposed instance of `Divisible`

{lang="text"}
~~~~~~~~
  new Divisible[JsEncoder] {
    ...
    def divide[A, B, C](fa: JsEncoder[A], fb: JsEncoder[B])(
      f: C => (A, B)
    ): JsEncoder[C] = { c =>
      val (a, b) = f(c)
      JsArray(IList(fa.toJson(a), fb.toJson(b)))
    }
  
    def conquer[A]: JsEncoder[A] = _ => JsNull
  }
~~~~~~~~

On one side of the composition laws, for a `String` input, we get

{lang="text"}
~~~~~~~~
  JsArray([JsArray([JsString(hello),JsString(hello)]),JsString(hello)])
~~~~~~~~

and on the other

{lang="text"}
~~~~~~~~
  JsArray([JsString(hello),JsArray([JsString(hello),JsString(hello)])])
~~~~~~~~

which are different. We could experiment with variations of the `divide`
implementation, but it will never satisfy the laws for all inputs.

We therefore cannot provide a `Divisible[JsEncoder]` because it would break the
mathematical laws and invalidates all the assumptions that users of `Divisible`
rely upon.

To aid in testing laws, Scalaz typeclasses contain the codified versions of
their laws on the typeclass itself. We can write an automated test, asserting
that the law fails, to remind us of this fact:

{lang="text"}
~~~~~~~~
  val D: Divisible[JsEncoder] = ...
  val S: JsEncoder[String] = JsEncoder[String]
  val E: Equal[JsEncoder[String]] = (p1, p2) => p1.toJson("hello") === p2.toJson("hello")
  assert(!D.divideLaw.composition(S, S, S)(E))
~~~~~~~~

On the other hand, a similar `JsDecoder` test meets the `Applicative` composition laws

{lang="text"}
~~~~~~~~
  final case class Comp(a: String, b: Int)
  object Comp {
    implicit val equal: Equal[Comp] = ...
    implicit val decoder: JsDecoder[Comp] = ...
  }
  
  def composeTest(j: JsValue) = {
    val A: Applicative[JsDecoder] = Applicative[JsDecoder]
    val fa: JsDecoder[Comp] = JsDecoder[Comp]
    val fab: JsDecoder[Comp => (String, Int)] = A.point(c => (c.a, c.b))
    val fbc: JsDecoder[((String, Int)) => (Int, String)] = A.point(_.swap)
    val E: Equal[JsDecoder[(Int, String)]] = (p1, p2) => p1.fromJson(j) === p2.fromJson(j)
    assert(A.applyLaw.composition(fbc, fab, fa)(E))
  }
~~~~~~~~

for some test data

{lang="text"}
~~~~~~~~
  composeTest(JsObject(IList("a" -> JsString("hello"), "b" -> JsInteger(1))))
  composeTest(JsNull)
  composeTest(JsObject(IList("a" -> JsString("hello"))))
  composeTest(JsObject(IList("b" -> JsInteger(1))))
~~~~~~~~

Now we are reasonably confident that our derived `MonadError` is lawful.

However, just because we have a test that passes for a small set of data does
not prove that the laws are satisfied. We must also reason through the
implementation to convince ourselves that it **should** satisfy the laws, and try
to propose corner cases where it could fail.

One way of generating a wide variety of test data is to use the [scalacheck](https://github.com/rickynils/scalacheck)
library, which provides an `Arbitrary` typeclass that integrates with most
testing frameworks to repeat a test with randomly generated data.

The `jsonformat` library provides an `Arbitrary[JsValue]` (everybody should
provide an `Arbitrary` for their ADTs!) allowing us to make use of Scalatest's
`forAll` feature:

{lang="text"}
~~~~~~~~
  forAll(SizeRange(10))((j: JsValue) => composeTest(j))
~~~~~~~~

This test gives us even more confidence that our typeclass meets the
`Applicative` composition laws. By checking all the laws on `Divisible` and
`MonadError` we also get **a lot** of smoke tests for free.

A> We must restrict `forAll` to have a `SizeRange` of `10`, which limits both
A> `JsObject` and `JsArray` to a maximum size of 10 elements. This avoids stack
A> overflows as larger numbers can generate gigantic JSON documents.


### `Decidable` and `Alt`

Where `Divisible` and `Applicative` give us typeclass derivation for products
(built from tuples), `Decidable` and `Alt` give us the coproducts (built from
nested disjunctions):

{lang="text"}
~~~~~~~~
  @typeclass trait Alt[F[_]] extends Applicative[F] with InvariantAlt[F] {
    def alt[A](a1: =>F[A], a2: =>F[A]): F[A]
  
    def altly1[Z, A1](a1: =>F[A1])(f: A1 => Z): F[Z] = ...
    def altly2[Z, A1, A2](a1: =>F[A1], a2: =>F[A2])(f: A1 \/ A2 => Z): F[Z] = ...
    def altly3 ...
    def altly4 ...
    ...
  }
  
  @typeclass trait Decidable[F[_]] extends Divisible[F] with InvariantAlt[F] {
    def choose1[Z, A1](a1: =>F[A1])(f: Z => A1): F[Z] = ...
    def choose2[Z, A1, A2](a1: =>F[A1], a2: =>F[A2])(f: Z => A1 \/ A2): F[Z] = ...
    def choose3 ...
    def choose4 ...
    ...
  }
~~~~~~~~

The four core typeclasses have symmetric signatures:

| Typeclass     | method    | given          | signature         | returns |
|------------- |--------- |-------------- |----------------- |------- |
| `Applicative` | `apply2`  | `F[A1], F[A2]` | `(A1, A2) => Z`   | `F[Z]`  |
| `Alt`         | `altly2`  | `F[A1], F[A2]` | `(A1 \/ A2) => Z` | `F[Z]`  |
| `Divisible`   | `divide2` | `F[A1], F[A2]` | `Z => (A1, A2)`   | `F[Z]`  |
| `Decidable`   | `choose2` | `F[A1], F[A2]` | `Z => (A1 \/ A2)` | `F[Z]`  |

supporting covariant products; covariant coproducts; contravariant products;
contravariant coproducts.

We can write a `Decidable[Equal]`, letting us derive `Equal` for any ADT!

{lang="text"}
~~~~~~~~
  implicit val decidable = new Decidable[Equal] {
    ...
    def choose2[Z, A1, A2](a1: =>Equal[A1], a2: =>Equal[A2])(
      f: Z => A1 \/ A2
    ): Equal[Z] = { (z1, z2) =>
      (f(z1), f(z2)) match {
        case (-\/(s), -\/(t)) => a1.equal(s, t)
        case (\/-(s), \/-(t)) => a2.equal(s, t)
        case _ => false
      }
    }
  }
~~~~~~~~

For an ADT

{lang="text"}
~~~~~~~~
  sealed abstract class Darth { def widen: Darth = this }
  final case class Vader(s: String, i: Int)  extends Darth
  final case class JarJar(i: Int, s: String) extends Darth
~~~~~~~~

where the products (`Vader` and `JarJar`) have an `Equal`

{lang="text"}
~~~~~~~~
  object Vader {
    private val g: Vader => (String, Int) = d => (d.s, d.i)
    implicit val equal: Equal[Vader] = Divisible[Equal].divide2(Equal[String], Equal[Int])(g)
  }
  object JarJar {
    private val g: JarJar => (Int, String) = d => (d.i, d.s)
    implicit val equal: Equal[JarJar] = Divisible[Equal].divide2(Equal[Int], Equal[String])(g)
  }
~~~~~~~~

we can derive the equal for the whole ADT

{lang="text"}
~~~~~~~~
  object Darth {
    private def g(t: Darth): Vader \/ JarJar = t match {
      case p @ Vader(_, _)  => -\/(p)
      case p @ JarJar(_, _) => \/-(p)
    }
    implicit val equal: Equal[Darth] = Decidable[Equal].choose2(Equal[Vader], Equal[JarJar])(g)
  }
  
  scala> Vader("hello", 1).widen === JarJar(1, "hello).widen
  false
~~~~~~~~

A> Scalaz 7.2 does not provide a `Decidable[Equal]` out of the box, because it was
A> a late addition.

Typeclasses that have an `Applicative` can be eligible for an `Alt`. If we want
to use our `Kleisli.iso` trick, we have to extend `IsomorphismMonadError` and
mix in `Alt`. Upgrade our `MonadError[Default, String]` to have an
`Alt[Default]`:

{lang="text"}
~~~~~~~~
  private type K[a] = Kleisli[String \/ ?, Unit, a]
  implicit val monad = new IsomorphismMonadError[Default, K, String] with Alt[Default] {
    override val G = MonadError[K, String]
    override val iso = ...
  
    def alt[A](a1: =>Default[A], a2: =>Default[A]): Default[A] = instance(a1.default)
  }
~~~~~~~~

A> The primitive of `Alt` is `alt`, much as the primitive of `Applicative` is `ap`,
A> but it often makes more sense to use `altly2` and `apply2` as the primitives
A> with the following overrides:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   override def ap[A, B](fa: =>F[A])(f: =>F[A => B]): F[B] =
A>     apply2(fa, f)((a, abc) => abc(a))
A>   
A>   override def alt[A](a1: =>F[A], a2: =>F[A]): F[A] = altly2(a1, a2) {
A>     case -\/(a) => a
A>     case \/-(a) => a
A>   }
A> ~~~~~~~~
A> 
A> Just don't forget to implement `apply2` and `altly2` or there will be an
A> infinite loop at runtime.

Letting us derive our `Default[Darth]`

{lang="text"}
~~~~~~~~
  object Darth {
    ...
    private def f(e: Vader \/ JarJar): Darth = e.merge
    implicit val default: Default[Darth] =
      Alt[Default].altly2(Default[Vader], Default[JarJar])(f)
  }
  object Vader {
    ...
    private val f: (String, Int) => Vader = Vader(_, _)
    implicit val default: Default[Vader] =
      Alt[Default].apply2(Default[String], Default[Int])(f)
  }
  object JarJar {
    ...
    private val f: (Int, String) => JarJar = JarJar(_, _)
    implicit val default: Default[JarJar] =
      Alt[Default].apply2(Default[Int], Default[String])(f)
  }
  
  scala> Default[Darth].default
  \/-(Vader())
~~~~~~~~

Returning to the `scalaz-deriving` typeclasses, the invariant parents of `Alt`
and `Decidable` are:

{lang="text"}
~~~~~~~~
  @typeclass trait InvariantApplicative[F[_]] extends InvariantFunctor[F] {
    def xproduct0[Z](f: =>Z): F[Z]
    def xproduct1[Z, A1](a1: =>F[A1])(f: A1 => Z, g: Z => A1): F[Z] = ...
    def xproduct2 ...
    def xproduct3 ...
    def xproduct4 ...
  }
  
  @typeclass trait InvariantAlt[F[_]] extends InvariantApplicative[F] {
    def xcoproduct1[Z, A1](a1: =>F[A1])(f: A1 => Z, g: Z => A1): F[Z] = ...
    def xcoproduct2 ...
    def xcoproduct3 ...
    def xcoproduct4 ...
  }
~~~~~~~~

supporting typeclasses with an `InvariantFunctor` like `Monoid` and `Semigroup`.


### Arbitrary Arity and `@deriving`

There are two problems with `InvariantApplicative` and `InvariantAlt`:

1.  they only support products of four fields and coproducts of four entries.
2.  there is a **lot** of boilerplate on the data type companions.

In this section we solve both problems with additional typeclasses introduced by
`scalaz-deriving`

{width=75%}
![](images/scalaz-deriving.png)

Effectively, our four central typeclasses `Applicative`, `Divisible`, `Alt` and
`Decidable` all get extended to arbitrary arity using the [iotaz](https://github.com/frees-io/iota) library, hence
the `z` postfix.

The iotaz library has three main types:

-   `TList` which describes arbitrary length chains of types
-   `Prod[A <: TList]` for products
-   `Cop[A <: TList]` for coproducts

By way of example, a `TList` representation of `Darth` from the previous
section is

{lang="text"}
~~~~~~~~
  import iotaz._, TList._
  
  type DarthT  = Vader  :: JarJar :: TNil
  type VaderT  = String :: Int    :: TNil
  type JarJarT = Int    :: String :: TNil
~~~~~~~~

which can be instantiated:

{lang="text"}
~~~~~~~~
  val vader: Prod[VaderT]    = Prod("hello", 1)
  val jarjar: Prod[JarJarT]  = Prod(1, "hello")
  
  val VaderI = Cop.Inject[Vader, Cop[DarthT]]
  val darth: Cop[DarthT] = VaderI.inj(Vader("hello", 1))
~~~~~~~~

To be able to use the `scalaz-deriving` API, we need an `Isomorphism` between
our ADTs and the `iotaz` generic representation. It is a lot of boilerplate,
we will get to that in a moment:

{lang="text"}
~~~~~~~~
  object Darth {
    private type Repr   = Vader :: JarJar :: TNil
    private val VaderI  = Cop.Inject[Vader, Cop[Repr]]
    private val JarJarI = Cop.Inject[JarJar, Cop[Repr]]
    private val iso     = IsoSet(
      {
        case d: Vader  => VaderI.inj(d)
        case d: JarJar => JarJarI.inj(d)
      }, {
        case VaderI(d)  => d
        case JarJarI(d) => d
      }
    )
    ...
  }
  
  object Vader {
    private type Repr = String :: Int :: TNil
    private val iso   = IsoSet(
      d => Prod(d.s, d.i),
      p => Vader(p.head, p.tail.head)
    )
    ...
  }
  
  object JarJar {
    private type Repr = Int :: String :: TNil
    private val iso   = IsoSet(
      d => Prod(d.i, d.s),
      p => JarJar(p.head, p.tail.head)
    )
    ...
  }
~~~~~~~~

With that out of the way we can call the `Deriving` API for `Equal`, possible
because `scalaz-deriving` provides an optimised instance of `Deriving[Equal]`

{lang="text"}
~~~~~~~~
  object Darth {
    ...
    implicit val equal: Equal[Darth] = Deriving[Equal].xcoproductz(
      Prod(Need(Equal[Vader]), Need(Equal[JarJar])))(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val equal: Equal[Vader] = Deriving[Equal].xproductz(
      Prod(Need(Equal[String]), Need(Equal[Int])))(iso.to, iso.from)
  }
  object JarJar {
    ...
    implicit val equal: Equal[JarJar] = Deriving[Equal].xproductz(
      Prod(Need(Equal[Int]), Need(Equal[String])))(iso.to, iso.from)
  }
~~~~~~~~

A> Typeclasses in the `Deriving` API are wrapped in `Need` (recall `Name` from
A> Chapter 6), which allows lazy construction, avoiding unnecessary work if the
A> typeclass is not needed, and avoiding stack overflows for recursive ADTs.

To be able to do the same for our `Default` typeclass, we need to provide an
instance of `Deriving[Default]`. This is just a case of wrapping our existing
`Alt` with a helper:

{lang="text"}
~~~~~~~~
  object Default {
    ...
    implicit val deriving: Deriving[Default] = ExtendedInvariantAlt(monad)
  }
~~~~~~~~

and then calling it from the companions

{lang="text"}
~~~~~~~~
  object Darth {
    ...
    implicit val default: Default[Darth] = Deriving[Default].xcoproductz(
      Prod(Need(Default[Vader]), Need(Default[JarJar])))(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val default: Default[Vader] = Deriving[Default].xproductz(
      Prod(Need(Default[String]), Need(Default[Int])))(iso.to, iso.from)
  }
  object JarJar {
    ...
    implicit val default: Default[JarJar] = Deriving[Default].xproductz(
      Prod(Need(Default[Int]), Need(Default[String])))(iso.to, iso.from)
  }
~~~~~~~~

We have solved the problem of arbitrary arity, but we have introduced even more
boilerplate.

The punchline is that the `@deriving` annotation, which comes from
`deriving-plugin`, generates all this boilerplate automatically and only needs
to be applied at the top level of an ADT:

{lang="text"}
~~~~~~~~
  @deriving(Equal, Default)
  sealed abstract class Darth { def widen: Darth = this }
  final case class Vader(s: String, i: Int)  extends Darth
  final case class JarJar(i: Int, s: String) extends Darth
~~~~~~~~

Also included in `scalaz-deriving` are instances for `Order`, `Semigroup` and
`Monoid`. Instances of `Show` and `Arbitrary` are available by installing the
`scalaz-deriving-magnolia` and `scalaz-deriving-scalacheck` extras.

You're welcome!


### Examples

We finish our study of `scalaz-deriving` with fully worked implementations of
all the example typeclasses. Before we do that we need to know about a new data
type: `/~\`, aka the *snake in the road*, for containing two higher kinded
structures that share the same type parameter:

{lang="text"}
~~~~~~~~
  sealed abstract class /~\[A[_], B[_]] {
    type T
    def a: A[T]
    def b: B[T]
  }
  object /~\ {
    type APair[A[_], B[_]]  = A /~\ B
    def unapply[A[_], B[_]](p: A /~\ B): Some[(A[p.T], B[p.T])] = ...
    def apply[A[_], B[_], Z](az: =>A[Z], bz: =>B[Z]): A /~\ B = ...
  }
~~~~~~~~

We typically use this in the context of `Id /~\ TC` where `TC` is our typeclass,
meaning that we have a value, and an instance of a typeclass for that value,
without knowing anything about the value.

In addition, all the methods on the `Deriving` API have implicit evidence of the
form `A PairedWith FA`, allowing the `iotaz` library to be able to perform
`.zip`, `.traverse`, and other operations on `Prod` and `Cop`. We can ignore
these parameters, as we don't use them directly.


#### `Equal`

As with `Default` we could define a regular fixed-arity `Decidable` and wrap it
with `ExtendedInvariantAlt` (the simplest approach), but we choose to implement
`Decidablez` directly for the performance benefit. We make two additional
optimisations:

1.  perform instance equality `.eq` before applying the `Equal.equal`, allowing
    for shortcut equality between identical values.
2.  `Foldable.all` allowing early exit when any comparison is `false`. e.g. if
    the first fields don't match, we don't even request the `Equal` for remaining
    values.

{lang="text"}
~~~~~~~~
  new Decidablez[Equal] {
    @inline private final def quick(a: Any, b: Any): Boolean =
      a.asInstanceOf[AnyRef].eq(b.asInstanceOf[AnyRef])
  
    def dividez[Z, A <: TList, FA <: TList](tcs: Prod[FA])(g: Z => Prod[A])(
      implicit ev: A PairedWith FA
    ): Equal[Z] = (z1, z2) => (g(z1), g(z2)).zip(tcs).all {
      case (a1, a2) /~\ fa => quick(a1, a2) || fa.value.equal(a1, a2)
    }
  
    def choosez[Z, A <: TList, FA <: TList](tcs: Prod[FA])(g: Z => Cop[A])(
      implicit ev: A PairedWith FA
    ): Equal[Z] = (z1, z2) => (g(z1), g(z2)).zip(tcs) match {
      case -\/(_)               => false
      case \/-((a1, a2) /~\ fa) => quick(a1, a2) || fa.value.equal(a1, a2)
    }
  }
~~~~~~~~


#### `Default`

Unfortunately, the `iotaz` API for `.traverse` (and its analogy, `.coptraverse`)
requires us to define natural transformations, which have a clunky syntax, even
with the `kind-projector` plugin.

{lang="text"}
~~~~~~~~
  private type K[a] = Kleisli[String \/ ?, Unit, a]
  new IsomorphismMonadError[Default, K, String] with Altz[Default] {
    type Sig[a] = Unit => String \/ a
    override val G = MonadError[K, String]
    override val iso = Kleisli.iso(
      λ[Sig ~> Default](s => instance(s(()))),
      λ[Default ~> Sig](d => _ => d.default)
    )
  
    val extract = λ[NameF ~> (String \/ ?)](a => a.value.default)
    def applyz[Z, A <: TList, FA <: TList](tcs: Prod[FA])(f: Prod[A] => Z)(
      implicit ev: A PairedWith FA
    ): Default[Z] = instance(tcs.traverse(extract).map(f))
  
    val always = λ[NameF ~> Maybe](a => a.value.default.toMaybe)
    def altlyz[Z, A <: TList, FA <: TList](tcs: Prod[FA])(f: Cop[A] => Z)(
      implicit ev: A PairedWith FA
    ): Default[Z] = instance {
      tcs.coptraverse[A, NameF, Id](always).map(f).headMaybe \/> "not found"
    }
  }
~~~~~~~~


#### `Semigroup`

It is not possible to define a `Semigroup` for general coproducts, however it is
possible to define one for general products. We can use the arbitrary arity
`InvariantApplicative`:

{lang="text"}
~~~~~~~~
  new InvariantApplicativez[Semigroup] {
    type L[a] = ((a, a), NameF[a])
    val appender = λ[L ~> Id] { case ((a1, a2), fa) => fa.value.append(a1, a2) }
  
    def xproductz[Z, A <: TList, FA <: TList](tcs: Prod[FA])
                                             (f: Prod[A] => Z, g: Z => Prod[A])
                                             (implicit ev: A PairedWith FA) =
      new Semigroup[Z] {
        def append(z1: Z, z2: =>Z): Z = f(tcs.ziptraverse2(g(z1), g(z2), appender))
      }
  }
~~~~~~~~


#### `JsEncoder` and `JsDecoder`

`scalaz-deriving` does not provide access to field names so it is not possible
to write a JSON encoder or decoder.

A> An earlier version of `scalaz-deriving` supported field names but it was clear
A> that there was no advantage over using Magnolia, so the support was dropped to
A> remain focused on typeclasses with lawful `Alt` and `Decidable`.


## Magnolia

The Magnolia macro library provides a clean API for writing typeclass
derivations. It is installed with the following `build.sbt` entry

{lang="text"}
~~~~~~~~
  libraryDependencies += "com.propensive" %% "magnolia" % "0.10.1"
~~~~~~~~

A typeclass author implements the following members:

{lang="text"}
~~~~~~~~
  import magnolia._
  
  object MyDerivation {
    type Typeclass[A]
  
    def combine[A](ctx: CaseClass[Typeclass, A]): Typeclass[A]
    def dispatch[A](ctx: SealedTrait[Typeclass, A]): Typeclass[A]
  
    def gen[A]: Typeclass[A] = macro Magnolia.gen[A]
  }
~~~~~~~~

The Magnolia API is:

{lang="text"}
~~~~~~~~
  class CaseClass[TC[_], A] {
    def typeName: TypeName
    def construct[B](f: Param[TC, A] => B): A
    def constructMonadic[F[_]: Monadic, B](f: Param[TC, A] => F[B]): F[A]
    def parameters: Seq[Param[TC, A]]
    def annotations: Seq[Any]
  }
  
  class SealedTrait[TC[_], A] {
    def typeName: TypeName
    def subtypes: Seq[Subtype[TC, A]]
    def dispatch[B](value: A)(handle: Subtype[TC, A] => B): B
    def annotations: Seq[Any]
  }
~~~~~~~~

with helpers

{lang="text"}
~~~~~~~~
  final case class TypeName(short: String, full: String)
  
  class Param[TC[_], A] {
    type PType
    def label: String
    def index: Int
    def typeclass: TC[PType]
    def dereference(param: A): PType
    def default: Option[PType]
    def annotations: Seq[Any]
  }
  
  class Subtype[TC[_], A] {
    type SType <: A
    def typeName: TypeName
    def index: Int
    def typeclass: TC[SType]
    def cast(a: A): SType
    def annotations: Seq[Any]
  }
~~~~~~~~

The `Monadic` typeclass, used in `constructMonadic`, is automatically generated
if our data type has a `.map` and `.flatMap` method when we `import mercator._`

It does not make sense to use Magnolia for typeclasses that can be abstracted by
`Divisible`, `Decidable`, `Applicative` or `Alt`, since those abstractions
provide a lot of extra structure and tests for free. However, Magnolia offers
features that `scalaz-deriving` cannot provide: access to field names, type
names, annotations and default values.


### Example: JSON

We have some design choices to make with regards to JSON serialisation:

1.  Should we include fields with `null` values?
2.  Should decoding treat missing vs `null` differently?
3.  How do we encode the name of a coproduct?
4.  How do we deal with coproducts that are not `JsObject`?

We choose sensible defaults

-   do not include fields if the value is a `JsNull`.
-   handle missing fields the same as `null` values.
-   use a special field `"type"` to disambiguate coproducts using the type name.
-   put primitive values into a special field `"xvalue"`.

and let the users attach an annotation to coproducts and product fields to
customise their formats:

{lang="text"}
~~~~~~~~
  sealed class json extends Annotation
  object json {
    final case class nulls()          extends json
    final case class field(f: String) extends json
    final case class hint(f: String)  extends json
  }
~~~~~~~~

A> Magnolia is not limited to one annotation family. This encoding is so that we
A> can do a like-for-like comparison with Shapeless in the next section.

For example

{lang="text"}
~~~~~~~~
  @json.field("TYPE")
  sealed abstract class Cost
  final case class Time(s: String) extends Cost
  final case class Money(@json.field("integer") i: Int) extends Cost
~~~~~~~~

Start with a `JsDecoder` that handles only our sensible defaults:

{lang="text"}
~~~~~~~~
  object JsMagnoliaEncoder {
    type Typeclass[A] = JsEncoder[A]
  
    def combine[A](ctx: CaseClass[JsEncoder, A]): JsEncoder[A] = { a =>
      val empty = IList.empty[(String, JsValue)]
      val fields = ctx.parameters.foldRight(right) { (p, acc) =>
        p.typeclass.toJson(p.dereference(a)) match {
          case JsNull => acc
          case value  => (p.label -> value) :: acc
        }
      }
      JsObject(fields)
    }
  
    def dispatch[A](ctx: SealedTrait[JsEncoder, A]): JsEncoder[A] = a =>
      ctx.dispatch(a) { sub =>
        val hint = "type" -> JsString(sub.typeName.short)
        sub.typeclass.toJson(sub.cast(a)) match {
          case JsObject(fields) => JsObject(hint :: fields)
          case other            => JsObject(IList(hint, "xvalue" -> other))
        }
      }
  
    def gen[A]: JsEncoder[A] = macro Magnolia.gen[A]
  }
~~~~~~~~

We can see how the Magnolia API makes it easy to access field names and
typeclasses for each parameter.

Now add support for annotations to handle user preferences. To avoid looking up
the annotations on every encoding, we will cache them in an array. Although field
access to an array is non-total, we are guaranteed that the indices will always
align. Performance is usually the victim in the trade-off between specialisation
and generalisation.

{lang="text"}
~~~~~~~~
  object JsMagnoliaEncoder {
    type Typeclass[A] = JsEncoder[A]
  
    def combine[A](ctx: CaseClass[JsEncoder, A]): JsEncoder[A] =
      new JsEncoder[A] {
        private val anns = ctx.parameters.map { p =>
          val nulls = p.annotations.collectFirst {
            case json.nulls() => true
          }.getOrElse(false)
          val field = p.annotations.collectFirst {
            case json.field(name) => name
          }.getOrElse(p.label)
          (nulls, field)
        }.toArray
  
        def toJson(a: A): JsValue = {
          val empty = IList.empty[(String, JsValue)]
          val fields = ctx.parameters.foldRight(empty) { (p, acc) =>
            val (nulls, field) = anns(p.index)
            p.typeclass.toJson(p.dereference(a)) match {
              case JsNull if !nulls => acc
              case value            => (field -> value) :: acc
            }
          }
          JsObject(fields)
        }
      }
  
    def dispatch[A](ctx: SealedTrait[JsEncoder, A]): JsEncoder[A] =
      new JsEncoder[A] {
        private val field = ctx.annotations.collectFirst {
          case json.field(name) => name
        }.getOrElse("type")
        private val anns = ctx.subtypes.map { s =>
          val hint = s.annotations.collectFirst {
            case json.hint(name) => field -> JsString(name)
          }.getOrElse(field -> JsString(s.typeName.short))
          val xvalue = s.annotations.collectFirst {
            case json.field(name) => name
          }.getOrElse("xvalue")
          (hint, xvalue)
        }.toArray
  
        def toJson(a: A): JsValue = ctx.dispatch(a) { sub =>
          val (hint, xvalue) = anns(sub.index)
          sub.typeclass.toJson(sub.cast(a)) match {
            case JsObject(fields) => JsObject(hint :: fields)
            case other            => JsObject(hint :: (xvalue -> other) :: IList.empty)
          }
        }
      }
  
    def gen[A]: JsEncoder[A] = macro Magnolia.gen[A]
  }
~~~~~~~~

For the decoder we use `.constructMonadic` which has a type signature similar to
`.traverse`

{lang="text"}
~~~~~~~~
  object JsMagnoliaDecoder {
    type Typeclass[A] = JsDecoder[A]
  
    def combine[A](ctx: CaseClass[JsDecoder, A]): JsDecoder[A] = {
      case obj @ JsObject(_) =>
        ctx.constructMonadic(
          p => p.typeclass.fromJson(obj.get(p.label).getOrElse(JsNull))
        )
      case other => fail("JsObject", other)
    }
  
    def dispatch[A](ctx: SealedTrait[JsDecoder, A]): JsDecoder[A] = {
      case obj @ JsObject(_) =>
        obj.get("type") match {
          case \/-(JsString(hint)) =>
            ctx.subtypes.find(_.typeName.short == hint) match {
              case None => fail(s"a valid '$hint'", obj)
              case Some(sub) =>
                val value = obj.get("xvalue").getOrElse(obj)
                sub.typeclass.fromJson(value)
            }
          case _ => fail("JsObject with type", obj)
        }
      case other => fail("JsObject", other)
    }
  
    def gen[A]: JsDecoder[A] = macro Magnolia.gen[A]
  }
~~~~~~~~

Again, adding support for user preferences and default field values, along with
some optimisations:

{lang="text"}
~~~~~~~~
  object JsMagnoliaDecoder {
    type Typeclass[A] = JsDecoder[A]
  
    def combine[A](ctx: CaseClass[JsDecoder, A]): JsDecoder[A] =
      new JsDecoder[A] {
        private val nulls = ctx.parameters.map { p =>
          p.annotations.collectFirst {
            case json.nulls() => true
          }.getOrElse(false)
        }.toArray
  
        private val fieldnames = ctx.parameters.map { p =>
          p.annotations.collectFirst {
            case json.field(name) => name
          }.getOrElse(p.label)
        }.toArray
  
        def fromJson(j: JsValue): String \/ A = j match {
          case obj @ JsObject(_) =>
            import mercator._
            val lookup = obj.fields.toMap
            ctx.constructMonadic { p =>
              val field = fieldnames(p.index)
              lookup
                .get(field)
                .into {
                  case Maybe.Just(value) => p.typeclass.fromJson(value)
                  case _ =>
                    p.default match {
                      case Some(default) => \/-(default)
                      case None if nulls(p.index) =>
                        s"missing field '$field'".left
                      case None => p.typeclass.fromJson(JsNull)
                    }
                }
            }
          case other => fail("JsObject", other)
        }
      }
  
    def dispatch[A](ctx: SealedTrait[JsDecoder, A]): JsDecoder[A] =
      new JsDecoder[A] {
        private val subtype = ctx.subtypes.map { s =>
          s.annotations.collectFirst {
            case json.hint(name) => name
          }.getOrElse(s.typeName.short) -> s
        }.toMap
        private val typehint = ctx.annotations.collectFirst {
          case json.field(name) => name
        }.getOrElse("type")
        private val xvalues = ctx.subtypes.map { sub =>
          sub.annotations.collectFirst {
            case json.field(name) => name
          }.getOrElse("xvalue")
        }.toArray
  
        def fromJson(j: JsValue): String \/ A = j match {
          case obj @ JsObject(_) =>
            obj.get(typehint) match {
              case \/-(JsString(h)) =>
                subtype.get(h) match {
                  case None => fail(s"a valid '$h'", obj)
                  case Some(sub) =>
                    val xvalue = xvalues(sub.index)
                    val value  = obj.get(xvalue).getOrElse(obj)
                    sub.typeclass.fromJson(value)
                }
              case _ => fail(s"JsObject with '$typehint' field", obj)
            }
          case other => fail("JsObject", other)
        }
      }
  
    def gen[A]: JsDecoder[A] = macro Magnolia.gen[A]
  }
~~~~~~~~

We call the `JsMagnoliaEncoder.gen` or `JsMagnoliaDecoder.gen` method from the
companion of our data types. For example, the Google Maps API

{lang="text"}
~~~~~~~~
  final case class Value(text: String, value: Int)
  final case class Elements(distance: Value, duration: Value, status: String)
  final case class Rows(elements: List[Elements])
  final case class DistanceMatrix(
    destination_addresses: List[String],
    origin_addresses: List[String],
    rows: List[Rows],
    status: String
  )
  
  object Value {
    implicit val encoder: JsEncoder[Value] = JsMagnoliaEncoder.gen
    implicit val decoder: JsDecoder[Value] = JsMagnoliaDecoder.gen
  }
  object Elements {
    implicit val encoder: JsEncoder[Elements] = JsMagnoliaEncoder.gen
    implicit val decoder: JsDecoder[Elements] = JsMagnoliaDecoder.gen
  }
  object Rows {
    implicit val encoder: JsEncoder[Rows] = JsMagnoliaEncoder.gen
    implicit val decoder: JsDecoder[Rows] = JsMagnoliaDecoder.gen
  }
  object DistanceMatrix {
    implicit val encoder: JsEncoder[DistanceMatrix] = JsMagnoliaEncoder.gen
    implicit val decoder: JsDecoder[DistanceMatrix] = JsMagnoliaDecoder.gen
  }
~~~~~~~~

Thankfully, the `@deriving` annotation supports Magnolia! If the typeclass
author provides a file `deriving.conf` with their jar, containing this text

{lang="text"}
~~~~~~~~
  jsonformat.JsEncoder=jsonformat.JsMagnoliaEncoder.gen
  jsonformat.JsDecoder=jsonformat.JsMagnoliaDecoder.gen
~~~~~~~~

the `deriving-macro` will call the user-provided method:

{lang="text"}
~~~~~~~~
  @deriving(JsEncoder, JsDecoder)
  final case class Value(text: String, value: Int)
  @deriving(JsEncoder, JsDecoder)
  final case class Elements(distance: Value, duration: Value, status: String)
  @deriving(JsEncoder, JsDecoder)
  final case class Rows(elements: List[Elements])
  @deriving(JsEncoder, JsDecoder)
  final case class DistanceMatrix(
    destination_addresses: List[String],
    origin_addresses: List[String],
    rows: List[Rows],
    status: String
  )
~~~~~~~~


### Fully Automatic Derivation

Generating `implicit` instances on the companion of the data type is
historically known as *semi-auto* derivation, in contrast to *full-auto* which
is when the `.gen` is made `implicit`

{lang="text"}
~~~~~~~~
  object JsMagnoliaEncoder {
    ...
    implicit def gen[A]: JsEncoder[A] = macro Magnolia.gen[A]
  }
  object JsMagnoliaDecoder {
    ...
    implicit def gen[A]: JsDecoder[A] = macro Magnolia.gen[A]
  }
~~~~~~~~

Users can import these methods into their scope and get magical derivation at
the point of use

{lang="text"}
~~~~~~~~
  scala> final case class Value(text: String, value: Int)
  scala> import JsMagnoliaEncoder.gen
  scala> Value("hello", 1).toJson
  res = JsObject([("text","hello"),("value",1)])
~~~~~~~~

This may sound tempting, as it involves the least amount of typing, but there
are two caveats:

1.  the macro is invoked at every use site, i.e. every time we call `.toJson`.
    This slows down compilation and also produces more objects at runtime, which
    will impact runtime performance.
2.  unexpected things may be derived.

The first caveat is self evident, but unexpected derivations manifests as
subtle bugs. Consider what would happen for

{lang="text"}
~~~~~~~~
  @deriving(JsEncoder)
  final case class Foo(s: Option[String])
~~~~~~~~

if we forgot to provide an implicit derivation for `Option`. We might expect a
`Foo(Some("hello"))` to look like

{lang="text"}
~~~~~~~~
  {
    "s":"hello"
  }
~~~~~~~~

But it would instead be

{lang="text"}
~~~~~~~~
  {
    "s": {
      "type":"Some",
      "get":"hello"
    }
  }
~~~~~~~~

because Magnolia derived an `Option` encoder for us.

This is confusing, we would rather have the compiler tell us if we forgot
something. Full auto is therefore not recommended.


## Shapeless

The [Shapeless](https://github.com/milessabin/shapeless/) library is notoriously the most complicated library in Scala. The
reason why it has such a reputation is because it takes the `implicit` language
feature to the extreme: creating a kind of *generic programming* language at the
level of the types.

This is not an entirely foreign concept: in Scalaz we try to limit our use of
the `implicit` language feature to typeclasses, but we sometimes ask the
compiler to provide us with *evidence* relating types. For example Liskov or
Leibniz relationship (`<~<` and `===`), and to `Inject` a free algebra into a
`scalaz.Coproduct` of algebras.

A> It is not necessary to understand Shapeless to be a Functional Programmer. If
A> this chapter becomes too much, just skip to the next section.

To install Shapeless, add the following to `build.sbt`

{lang="text"}
~~~~~~~~
  libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.3"
~~~~~~~~

At the core of Shapeless are the `HList` and `Coproduct` data types

{lang="text"}
~~~~~~~~
  package shapeless
  
  sealed trait HList
  final case class ::[+H, +T <: HList](head: H, tail: T) extends HList
  sealed trait NNil extends HList
  case object HNil extends HNil {
    def ::[H](h: H): H :: HNil = ::(h, this)
  }
  
  sealed trait Coproduct
  sealed trait :+:[+H, +T <: Coproduct] extends Coproduct
  final case class Inl[+H, +T <: Coproduct](head: H) extends :+:[H, T]
  final case class Inr[+H, +T <: Coproduct](tail: T) extends :+:[H, T]
  sealed trait CNil extends Coproduct // no implementations
~~~~~~~~

which are *generic* representations of products and coproducts, respectively.
The `sealed trait HNil` is for convenience so we never need to type `HNil.type`.

Shapeless has a clone of the `IsoSet` datatype, called `Generic`, which allows
us to move between an ADT and its generic representation:

{lang="text"}
~~~~~~~~
  trait Generic[T] {
    type Repr
    def to(t: T): Repr
    def from(r: Repr): T
  }
  object Generic {
    type Aux[T, R] = Generic[T] { type Repr = R }
    def apply[T](implicit G: Generic[T]): Aux[T, G.Repr] = G
    implicit def materialize[T, R]: Aux[T, R] = macro ...
  }
~~~~~~~~

Many of the types in Shapeless have a type member (`Repr`) and an `.Aux` type
alias on their companion that makes the second type visible. This allows us to
request the `Generic[Foo]` for a type `Foo` without having to provide the
generic representation, which is generated by a macro.

{lang="text"}
~~~~~~~~
  scala> import shapeless._
  scala> final case class Foo(a: String, b: Long)
         Generic[Foo].to(Foo("hello", 13L))
  res: String :: Long :: HNil = hello :: 13 :: HNil
  
  scala> Generic[Foo].from("hello" :: 13L :: HNil)
  res: Foo = Foo(hello,13)
  
  scala> sealed abstract class Bar
         case object Irish extends Bar
         case object English extends Bar
  
  scala> Generic[Bar].to(Irish)
  res: English.type :+: Irish.type :+: CNil.type = Inl(Irish)
  
  scala> Generic[Bar].from(Inl(Irish))
  res: Bar = Irish
~~~~~~~~

There is a complementary `LabelledGeneric` that includes the field names

{lang="text"}
~~~~~~~~
  scala> import shapeless._, labelled._
  scala> final case class Foo(a: String, b: Long)
  
  scala> LabelledGeneric[Foo].to(Foo("hello", 13L))
  res: String with KeyTag[Symbol with Tagged[String("a")], String] ::
       Long   with KeyTag[Symbol with Tagged[String("b")],   Long] ::
       HNil =
       hello :: 13 :: HNil
  
  scala> sealed abstract class Bar
         case object Irish extends Bar
         case object English extends Bar
  
  scala> LabelledGeneric[Bar].to(Irish)
  res: Irish.type   with KeyTag[Symbol with Tagged[String("Irish")],     Irish.type] :+:
       English.type with KeyTag[Symbol with Tagged[String("English")], English.type] :+:
       CNil.type =
       Inl(Irish)
~~~~~~~~

Note that the **value** of a `LabelledGeneric` representation is the same as the
`Generic` representation: field names only exist in the type and are erased at
runtime.

We never need to type `KeyTag` manually, we use the type alias:

{lang="text"}
~~~~~~~~
  type FieldType[K, +V] = V with KeyTag[K, V]
~~~~~~~~

If we want to access the field name from a `FieldType[K, A]`, we ask for
implicit evidence `Witness.Aux[K]`, which allows us to access the value of `K`
at runtime.

Superficially, this is all we need to know about Shapeless to be able to derive
a typeclass. However, things get increasingly complex, so we will proceed with
increasingly complex examples.


### Example: Equal

A typical pattern to follow is to extend the typeclass that we wish to derive,
and put the Shapeless code on its companion. This gives us an implicit scope
that the compiler can search without requiring complex imports

{lang="text"}
~~~~~~~~
  trait DerivedEqual[A] extends Equal[A]
  object DerivedEqual {
    ...
  }
~~~~~~~~

The entry point to a Shapeless derivation is a method, `gen`, requiring two type
parameters: the `A` that we are deriving and the `R` for its generic
representation. We then ask for the `Generic.Aux[A, R]`, relating `A` to `R`,
and an instance of the `Derived` typeclass for the `R`. We begin with this
signature and simple implementation:

{lang="text"}
~~~~~~~~
  import shapeless._
  
  object DerivedEqual {
    def gen[A, R: DerivedEqual](implicit G: Generic.Aux[A, R]): Equal[A] =
      (a1, a2) => Equal[R].equal(G.to(a1), G.to(a2))
  }
~~~~~~~~

We've reduced the problem to providing an implicit `Equal[R]` for an `R` that is
the `Generic` representation of `A`. First consider products, where `R <:
HList`. This is the signature we want to implement:

{lang="text"}
~~~~~~~~
  implicit def hcons[H: Equal, T <: HList: DerivedEqual]: DerivedEqual[H :: T]
~~~~~~~~

because if we can implement it for a head and a tail, the compiler will be able
to recurse on this method until it reaches the end of the list. Where we will
need to provide an instance for the empty `HNil`

{lang="text"}
~~~~~~~~
  implicit def hnil: DerivedEqual[HNil]
~~~~~~~~

We implement these methods

{lang="text"}
~~~~~~~~
  implicit def hcons[H: Equal, T <: HList: DerivedEqual]: DerivedEqual[H :: T] =
    (h1, h2) => Equal[H].equal(h1.head, h2.head) && Equal[T].equal(h1.tail, h2.tail)
  
  implicit val hnil: DerivedEqual[HNil] = (_, _) => true
~~~~~~~~

and for coproducts we want to implement these signatures

{lang="text"}
~~~~~~~~
  implicit def ccons[H: Equal, T <: Coproduct: DerivedEqual]: DerivedEqual[H :+: T]
  implicit def cnil: DerivedEqual[CNil]
~~~~~~~~

A> Scalaz and Shapeless share many type names, when mixing them we often need to
A> exclude certain elements from the import, e.g.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   import scalaz.{ Coproduct => _, :+: => _, _ }, Scalaz._
A>   import shapeless._
A> ~~~~~~~~

`.cnil` will never be called for a typeclass like `Equal` with type parameters
only in contravariant position, but the compiler doesn't know that so we have to
provide a stub:

{lang="text"}
~~~~~~~~
  implicit val cnil: DerivedEqual[CNil] = (_, _) => sys.error("impossible")
~~~~~~~~

For the coproduct case we can only compare two things if they align, which is
when they are both `Inl` or `Inr`

{lang="text"}
~~~~~~~~
  implicit def ccons[H: Equal, T <: Coproduct: DerivedEqual]: DerivedEqual[H :+: T] = {
    case (Inl(c1), Inl(c2)) => Equal[H].equal(c1, c2)
    case (Inr(c1), Inr(c2)) => Equal[T].equal(c1, c2)
    case _                  => false
  }
~~~~~~~~

It is noteworthy that our methods align with the concept of `conquer` (`hnil`),
`divide2` (`hlist`) and `alt2` (`coproduct`)! However, we don't get any of the
advantages of implementing `Decidable`, as now we must start from scratch when
writing tests for this code.

So let's test this thing with a simple ADT

{lang="text"}
~~~~~~~~
  sealed abstract class Foo
  final case class Bar(s: String)          extends Foo
  final case class Faz(b: Boolean, i: Int) extends Foo
  final case object Baz                    extends Foo
~~~~~~~~

We need to provide instances on the companions:

{lang="text"}
~~~~~~~~
  object Foo {
    implicit val equal: Equal[Foo] = DerivedEqual.gen
  }
  object Bar {
    implicit val equal: Equal[Bar] = DerivedEqual.gen
  }
  object Faz {
    implicit val equal: Equal[Faz] = DerivedEqual.gen
  }
  final case object Baz extends Foo {
    implicit val equal: Equal[Baz.type] = DerivedEqual.gen
  }
~~~~~~~~

But it doesn't compile

{lang="text"}
~~~~~~~~
  [error] shapeless.scala:41:38: ambiguous implicit values:
  [error]  both value hnil in object DerivedEqual of type => DerivedEqual[HNil]
  [error]  and value cnil in object DerivedEqual of type => DerivedEqual[CNil]
  [error]  match expected type DerivedEqual[R]
  [error]     : Equal[Baz.type] = DerivedEqual.gen
  [error]                                      ^
~~~~~~~~

Welcome to Shapeless compilation errors!

The problem, which is not at all evident from the error, is that the compiler is
unable to work out what `R` is, and gets caught thinking it is something else.
We need to provide the explicit type parameters when calling `gen`, e.g.

{lang="text"}
~~~~~~~~
  implicit val equal: Equal[Baz.type] = DerivedEqual.gen[Baz.type, HNil]
~~~~~~~~

or we can use the `Generic` macro to help us and let the compiler infer the generic representation

{lang="text"}
~~~~~~~~
  final case object Baz extends Foo {
    implicit val generic                = Generic[Baz.type]
    implicit val equal: Equal[Baz.type] = DerivedEqual.gen[Baz.type, generic.Repr]
  }
  ...
~~~~~~~~

A> At this point, ignore any red squigglies and only trust the compiler. This is
A> the point where Shapeless departs from IDE support.

The reason why this fixes the problem is because the type signature

{lang="text"}
~~~~~~~~
  def gen[A, R: DerivedEqual](implicit G: Generic.Aux[A, R]): Equal[A]
~~~~~~~~

desugars into

{lang="text"}
~~~~~~~~
  def gen[A, R](implicit R: DerivedEqual[R], G: Generic.Aux[A, R]): Equal[A]
~~~~~~~~

The Scala compiler solves type constraints left to right, so it finds many
different solutions to `DerivedEqual[R]` before constraining it with the
`Generic.Aux[A, R]`. Another way to solve this is to not use context bounds.

A> Rather than present the fully working version, we feel it is important to show
A> when obvious code fails, such is the reality of Shapeless. Another thing we
A> could have reasonably done here is to have `sealed` the `DerivedEqual` trait so
A> that only derived versions are valid. But `sealed trait` is not compatible with
A> SAM types! Living at the razor's edge, expect to get cut.

With this in mind, we no longer need the `implicit val generic` or the explicit
type parameters on the call to `.gen`. We can wire up `@deriving` by adding an
entry in `deriving.conf` (assuming we want to override the `scalaz-deriving`
implementation)

{lang="text"}
~~~~~~~~
  scalaz.Equal=fommil.DerivedEqual.gen
~~~~~~~~

and write

{lang="text"}
~~~~~~~~
  @deriving(Equal) sealed abstract class Foo
  @deriving(Equal) final case class Bar(s: String)          extends Foo
  @deriving(Equal) final case class Faz(b: Boolean, i: Int) extends Foo
  @deriving(Equal) final case object Baz
~~~~~~~~

But replacing the `scalaz-deriving` version means that compile times get slower.
This is because the compiler is solving `N` implicit searches for each product
of `N` fields or coproduct of `N` products, whereas `scalaz-deriving` and
Magnolia do not.

Note that when using `scalaz-deriving` or Magnolia we can put the `@deriving` on
just the top member of an ADT, but for Shapeless we must add it to all entries.

However, this implementation still has a bug: it fails for recursive types **at runtime**, e.g.

{lang="text"}
~~~~~~~~
  @deriving(Equal) sealed trait ATree
  @deriving(Equal) final case class Leaf(value: String)               extends ATree
  @deriving(Equal) final case class Branch(left: ATree, right: ATree) extends ATree
~~~~~~~~

{lang="text"}
~~~~~~~~
  scala> val leaf1: Leaf    = Leaf("hello")
         val leaf2: Leaf    = Leaf("goodbye")
         val branch: Branch = Branch(leaf1, leaf2)
         val tree1: ATree   = Branch(leaf1, branch)
         val tree2: ATree   = Branch(leaf2, branch)
  
  scala> assert(tree1 /== tree2)
  [error] java.lang.NullPointerException
  [error] at DerivedEqual$.shapes$DerivedEqual$$$anonfun$hcons$1(shapeless.scala:16)
          ...
~~~~~~~~

The reason why this happens is because `Equal[Tree]` depends on the
`Equal[Branch]`, which depends on the `Equal[Tree]`. Recursion and BANG!
It must be loaded lazily, not eagerly.

Both `scalaz-deriving` and Magnolia deal with lazy automatically, but in
Shapeless it is the responsibility of the typeclass author.

The macro types `Cached`, `Strict` and `Lazy` modify the compiler's type
inference behaviour allowing us to achieve the laziness we require. The pattern
to follow is to use `Cached[Strict[_]]` on the entry point and `Lazy[_]` around
the `H` instances.

It is best to depart from context bounds and SAM types entirely at this point:

{lang="text"}
~~~~~~~~
  sealed trait DerivedEqual[A] extends Equal[A]
  object DerivedEqual {
    def gen[A, R](
      implicit G: Generic.Aux[A, R],
      R: Cached[Strict[DerivedEqual[R]]]
    ): Equal[A] = new Equal[A] {
      def equal(a1: A, a2: A) =
        quick(a1, a2) || R.value.value.equal(G.to(a1), G.to(a2))
    }
  
    implicit def hcons[H, T <: HList](
      implicit H: Lazy[Equal[H]],
      T: DerivedEqual[T]
    ): DerivedEqual[H :: T] = new DerivedEqual[H :: T] {
      def equal(ht1: H :: T, ht2: H :: T) =
        (quick(ht1.head, ht2.head) || H.value.equal(ht1.head, ht2.head)) &&
          T.equal(ht1.tail, ht2.tail)
    }
  
    implicit val hnil: DerivedEqual[HNil] = new DerivedEqual[HNil] {
      def equal(@unused h1: HNil, @unused h2: HNil) = true
    }
  
    implicit def ccons[H, T <: Coproduct](
      implicit H: Lazy[Equal[H]],
      T: DerivedEqual[T]
    ): DerivedEqual[H :+: T] = new DerivedEqual[H :+: T] {
      def equal(ht1: H :+: T, ht2: H :+: T) = (ht1, ht2) match {
        case (Inl(c1), Inl(c2)) => quick(c1, c2) || H.value.equal(c1, c2)
        case (Inr(c1), Inr(c2)) => T.equal(c1, c2)
        case _                  => false
      }
    }
  
    implicit val cnil: DerivedEqual[CNil] = new DerivedEqual[CNil] {
      def equal(@unused c1: CNil, @unused c2: CNil) = sys.error("impossible")
    }
  
    @inline private final def quick(a: Any, b: Any): Boolean =
      a.asInstanceOf[AnyRef].eq(b.asInstanceOf[AnyRef])
  }
~~~~~~~~

While we were at it, we optimised using the `quick` shortcut from
`scalaz-deriving`.

We can now call

{lang="text"}
~~~~~~~~
  assert(tree1 /== tree2)
~~~~~~~~

without a runtime exception.


### Example: `Default`

There are no new snares in the implementation of a typeclass with a type
parameter in covariant position. Here we create `HList` and `Coproduct` values,
and must provide a value for the `CNil` case as it corresponds to the case where
no coproduct is able to provide a value.

{lang="text"}
~~~~~~~~
  sealed trait DerivedDefault[A] extends Default[A]
  object DerivedDefault {
    def gen[A, R](
      implicit G: Generic.Aux[A, R],
      R: Cached[Strict[DerivedDefault[R]]]
    ): Default[A] = new Default[A] {
      def default = R.value.value.default.map(G.from)
    }
  
    implicit def hcons[H, T <: HList](
      implicit H: Lazy[Default[H]],
      T: DerivedDefault[T]
    ): DerivedDefault[H :: T] = new DerivedDefault[H :: T] {
      def default =
        for {
          head <- H.value.default
          tail <- T.default
        } yield head :: tail
    }
  
    implicit val hnil: DerivedDefault[HNil] = new DerivedDefault[HNil] {
      def default = HNil.right
    }
  
    implicit def ccons[H, T <: Coproduct](
      implicit H: Lazy[Default[H]],
      T: DerivedDefault[T]
    ): DerivedDefault[H :+: T] = new DerivedDefault[H :+: T] {
      def default = H.value.default.map(Inl(_)).orElse(T.default.map(Inr(_)))
    }
  
    implicit val cnil: DerivedDefault[CNil] = new DerivedDefault[CNil] {
      def default = "not a valid coproduct".left
    }
  }
~~~~~~~~

Much as we could draw an analogy between `Equal` and `Decidable`, we can see the
relationship to `Alt` in `.point` (`hnil`), `.apply2` (`.hcons`) and `.altly2`
(`.ccons`).

There is little to be learned from an example like `Semigroup`, so we will skip
to encoders and decoders.


### Example: `JsEncoder`

To be able to reproduce our Magnolia JSON encoder, we must be able to access:

1.  field names and class names
2.  annotations for user preferences
3.  default values on a `case class`

We will begin by creating an encoder that handles only the sensible defaults.

To get field names, we use `LabelledGeneric` instead of `Generic`, and when
defining the type of the head element, use `FieldType[K, H]` instead of just
`H`. A `Witness.Aux[K]` provides the value of the field name at runtime.

All of our methods are going to return `JsObject`, so rather than returning a
`JsValue` we can specialise and create `DerivedJsEncoder` that has a different
type signature to `JsEncoder`.

{lang="text"}
~~~~~~~~
  import shapeless._, labelled._
  
  sealed trait DerivedJsEncoder[R] {
    def toJsFields(r: R): IList[(String, JsValue)]
  }
  object DerivedJsEncoder {
    def gen[A, R](
      implicit G: LabelledGeneric.Aux[A, R],
      R: Cached[Strict[DerivedJsEncoder[R]]]
    ): JsEncoder[A] = new JsEncoder[A] {
      def toJson(a: A) = JsObject(R.value.value.toJsFields(G.to(a)))
    }
  
    implicit def hcons[K <: Symbol, H, T <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[T]
    ): DerivedJsEncoder[FieldType[K, H] :: T] =
      new DerivedJsEncoder[A, FieldType[K, H] :: T] {
        private val field = K.value.name
        def toJsFields(ht: FieldType[K, H] :: T) =
          ht match {
            case head :: tail =>
              val rest = T.toJsFields(tail)
              H.value.toJson(head) match {
                case JsNull => rest
                case value  => (field -> value) :: rest
              }
          }
      }
  
    implicit val hnil: DerivedJsEncoder[HNil] =
      new DerivedJsEncoder[HNil] {
        def toJsFields(h: HNil) = IList.empty
      }
  
    implicit def ccons[K <: Symbol, H, T <: Coproduct](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[T]
    ): DerivedJsEncoder[FieldType[K, H] :+: T] =
      new DerivedJsEncoder[FieldType[K, H] :+: T] {
        private val hint = ("type" -> JsString(K.value.name))
        def toJsFields(ht: FieldType[K, H] :+: T) = ht match {
          case Inl(head) =>
            H.value.toJson(head) match {
              case JsObject(fields) => hint :: fields
              case v                => IList.single("xvalue" -> v)
            }
  
          case Inr(tail) => T.toJsFields(tail)
        }
      }
  
    implicit val cnil: DerivedJsEncoder[CNil] =
      new DerivedJsEncoder[CNil] {
        def toJsFields(c: CNil) = sys.error("impossible")
      }
  
  }
~~~~~~~~

A> A pattern has emerged in many Shapeless derivation libraries that introduce
A> "hints" with a default `implicit`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   trait ProductHint[A] {
A>     def nulls(field: String): Boolean
A>     def fieldname(field: String): String
A>   }
A>   object ProductHint {
A>     implicit def default[A]: ProductHint[A] = new ProductHint[A] {
A>       def nulls(field: String)     = false
A>       def fieldname(field: String) = field
A>     }
A>   }
A> ~~~~~~~~
A> 
A> Users are supposed to provide a custom instance of `ProductHint` on their
A> companions or package objects. This is a **terrible idea** that relies on fragile
A> implicit ordering and is a source of typeclass decoherence: if we derive a
A> `JsEncoder[Foo]`, we will get a different result depending on which
A> `ProductHint[Foo]` is in scope. It is best avoided.

Shapeless selects codepaths at compiletime based on the presence of annotations,
which can lead to more optimised code, at the expense of code repetition. This
means that the number of annotations we are dealing with, and their subtypes,
must be manageable or we can find ourselves writing 10x the amount of code. We
change our three annotations into one containing all the customisation
parameters:

{lang="text"}
~~~~~~~~
  case class json(
    nulls: Boolean,
    field: Option[String],
    hint: Option[String]
  ) extends Annotation
~~~~~~~~

All users of the annotation must provide all three values since default values
and convenience methods are not available to annotation constructors. We can
write custom extractors so we don't have to change our Magnolia code

{lang="text"}
~~~~~~~~
  object json {
    object nulls {
      def unapply(j: json): Boolean = j.nulls
    }
    object field {
      def unapply(j: json): Option[String] = j.field
    }
    object hint {
      def unapply(j: json): Option[String] = j.hint
    }
  }
~~~~~~~~

We can request `Annotation[json, A]` for a `case class` or `sealed trait` to get access to the annotation, but we must write an `hcons` and a `ccons` dealing with both cases because the evidence will not be generated if the annotation is not present. We therefore have to introduce a lower priority implicit scope and put the "no annotation" evidence there.

We can also request `Annotations.Aux[json, A, J]` evidence to obtain an `HList`
of the `json` annotation for type `A`. Again, we must provide `hcons` and
`ccons` dealing with the case where there is and is not an annotation.

To support this one annotation, we must write four times as much code as before!

Lets start by rewriting the `JsEncoder`, only handling user code that doesn't
have any annotations. Now any code that uses the `@json` will fail to compile,
which is a good safety net.

We must add an `A` and `J` type to the `DerivedJsEncoder` and thread through the
annotations on its `.toJsObject` method. Our `.hcons` and `.ccons` evidence now
provides instances for `DerivedJsEncoder` with a `None.type` annotation and we
move them to a lower priority so that we can deal with `Annotation[json, A]` in
the higher priority.

Note that the evidence for `J` is listed before `R`. This is important, since
the compiler must first fix the type of `J` before it can solve for `R`.

{lang="text"}
~~~~~~~~
  sealed trait DerivedJsEncoder[A, R, J <: HList] {
    def toJsFields(r: R, anns: J): IList[(String, JsValue)]
  }
  object DerivedJsEncoder extends DerivedJsEncoder1 {
    def gen[A, R, J <: HList](
      implicit
      G: LabelledGeneric.Aux[A, R],
      J: Annotations.Aux[json, A, J],
      R: Cached[Strict[DerivedJsEncoder[A, R, J]]]
    ): JsEncoder[A] = new JsEncoder[A] {
      def toJson(a: A) = JsObject(R.value.value.toJsFields(G.to(a), J()))
    }
  
    implicit def hnil[A]: DerivedJsEncoder[A, HNil, HNil] =
      new DerivedJsEncoder[A, HNil, HNil] {
        def toJsFields(h: HNil, a: HNil) = IList.empty
      }
  
    implicit def cnil[A]: DerivedJsEncoder[A, CNil, HNil] =
      new DerivedJsEncoder[A, CNil, HNil] {
        def toJsFields(c: CNil, a: HNil) = sys.error("impossible")
      }
  }
  private[jsonformat] trait DerivedJsEncoder1 {
    implicit def hcons[A, K <: Symbol, H, T <: HList, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :: T, None.type :: J] =
      new DerivedJsEncoder[A, FieldType[K, H] :: T, None.type :: J] {
        private val field = K.value.name
        def toJsFields(ht: FieldType[K, H] :: T, anns: None.type :: J) =
          ht match {
            case head :: tail =>
              val rest = T.toJsFields(tail, anns.tail)
              H.value.toJson(head) match {
                case JsNull => rest
                case value  => (field -> value) :: rest
              }
          }
      }
  
    implicit def ccons[A, K <: Symbol, H, T <: Coproduct, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :+: T, None.type :: J] =
      new DerivedJsEncoder[A, FieldType[K, H] :+: T, None.type :: J] {
        private val hint = ("type" -> JsString(K.value.name))
        def toJsFields(ht: FieldType[K, H] :+: T, anns: None.type :: J) =
          ht match {
            case Inl(head) =>
              H.value.toJson(head) match {
                case JsObject(fields) => hint :: fields
                case v                => IList.single("xvalue" -> v)
              }
            case Inr(tail) => T.toJsFields(tail, anns.tail)
          }
      }
  }
~~~~~~~~

Now we can add the type signatures for the six new methods, covering all the
possibilities of where the annotation can be. Note that we only support **one**
annotation in each position. If the user provides multiple annotations, anything
after the first will be silently ignored.

We're now running out of names for things, so we will arbitrarily call it
`Annotated` when there is an annotation on the `A`, and `Custom` when there is
an annotation on a field:

{lang="text"}
~~~~~~~~
  object DerivedJsEncoder extends DerivedJsEncoder1 {
    ...
    implicit def hconsAnnotated[A, K <: Symbol, H, T <: HList, J <: HList](
      implicit
      A: Annotation[json, A],
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :: T, None.type :: J]
  
    implicit def cconsAnnotated[A, K <: Symbol, H, T <: Coproduct, J <: HList](
      implicit
      A: Annotation[json, A],
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :+: T, None.type :: J]
  
    implicit def hconsAnnotatedCustom[A, K <: Symbol, H, T <: HList, J <: HList](
      implicit
      A: Annotation[json, A],
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :: T, Some[json] :: J]
  
    implicit def cconsAnnotatedCustom[A, K <: Symbol, H, T <: Coproduct, J <: HList](
      implicit
      A: Annotation[json, A],
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :+: T, Some[json] :: J]
  }
  private[jsonformat] trait DerivedJsEncoder1 {
    ...
    implicit def hconsCustom[A, K <: Symbol, H, T <: HList, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :: T, Some[json] :: J] = ???
  
    implicit def cconsCustom[A, K <: Symbol, H, T <: Coproduct, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :+: T, Some[json] :: J]
  }
~~~~~~~~

We don't actually need `.hconsAnnotated` or `.hconsAnnotatedCustom` for
anything, since an annotation on a `case class` does not mean anything to the
encoding of that product, it is only used in `.cconsAnnotated*`. We can therefore
delete two methods.

`.cconsAnnotated` and `.cconsAnnotatedCustom` can be defined as

{lang="text"}
~~~~~~~~
  new DerivedJsEncoder[A, FieldType[K, H] :+: T, None.type :: J] {
    private val hint = A().field.getOrElse("type") -> JsString(K.value.name)
    def toJsFields(ht: FieldType[K, H] :+: T, anns: None.type :: J) = ht match {
      case Inl(head) =>
        H.value.toJson(head) match {
          case JsObject(fields) => hint :: fields
          case v                => IList.single("xvalue" -> v)
        }
      case Inr(tail) => T.toJsFields(tail, anns.tail)
    }
  }
~~~~~~~~

and

{lang="text"}
~~~~~~~~
  new DerivedJsEncoder[A, FieldType[K, H] :+: T, Some[json] :: J] {
    private val hintfield = A().field.getOrElse("type")
    def toJsFields(ht: FieldType[K, H] :+: T, anns: Some[json] :: J) = ht match {
      case Inl(head) =>
        val ann = anns.head.get
        H.value.toJson(head) match {
          case JsObject(fields) =>
            val hint = (hintfield -> JsString(ann.hint.getOrElse(K.value.name)))
            hint :: fields
          case v =>
            val xvalue = ann.field.getOrElse("xvalue")
            IList.single(xvalue -> v)
        }
      case Inr(tail) => T.toJsFields(tail, anns.tail)
    }
  }
~~~~~~~~

The use of `.head` and `.get` may be concerned but recall that the types here
are `::` and `Some` meaning that these methods are total and safe to use.

`.hconsCustom` and `.cconsCustom` are written

{lang="text"}
~~~~~~~~
  new DerivedJsEncoder[A, FieldType[K, H] :: T, Some[json] :: J] {
    def toJsFields(ht: FieldType[K, H] :: T, anns: Some[json] :: J) = ht match {
      case head :: tail =>
        val ann  = anns.head.get
        val next = T.toJsFields(tail, anns.tail)
        H.value.toJson(head) match {
          case JsNull if !ann.nulls => next
          case value =>
            val field = ann.field.getOrElse(K.value.name)
            (field -> value) :: next
        }
    }
  }
~~~~~~~~

and

{lang="text"}
~~~~~~~~
  new DerivedJsEncoder[A, FieldType[K, H] :+: T, Some[json] :: J] {
    def toJsFields(ht: FieldType[K, H] :+: T, anns: Some[json] :: J) = ht match {
      case Inl(head) =>
        val ann = anns.head.get
        H.value.toJson(head) match {
          case JsObject(fields) =>
            val hint = ("type" -> JsString(ann.hint.getOrElse(K.value.name)))
            hint :: fields
          case v =>
            val xvalue = ann.field.getOrElse("xvalue")
            IList.single(xvalue -> v)
        }
      case Inr(tail) => T.toJsFields(tail, anns.tail)
    }
  }
~~~~~~~~

Obviously, there is a lot of boilerplate, but looking closely one can see that
each method is implemented as efficiently as possible with the information it
has available: codepaths are selected at compiletime rather than runtime.

The performance obsessed may be able to refactor this code so all annotation
information is available in advance, rather than injected via the `.toJsFields`
method, with another layer of indirection. For absolute performance, we could
also treat each customisation as a separate annotation, but that would multiply
the amount of code we've written yet again, with additional cost to compilation
time on downstream users. Such optimisations are beyond the scope of this book,
but they are possible and people do them: the ability to shift work from runtime
to compiletime is one of the most appealing things about generic programming.

One more caveat that we need to be aware of: [`LabelledGeneric` is not compatible
with `scalaz.@@`](https://github.com/milessabin/shapeless/issues/309), but there is a workaround. Say we want to effectively ignore
tags so we add the following derivation rules to the companions of our encoder
and decoder

{lang="text"}
~~~~~~~~
  object JsEncoder {
    ...
    implicit def tagged[A: JsEncoder, Z]: JsEncoder[A @@ Z] =
      JsEncoder[A].contramap(Tag.unwrap)
  }
  object JsDecoder {
    ...
    implicit def tagged[A: JsDecoder, Z]: JsDecoder[A @@ Z] =
      JsDecoder[A].map(Tag(_))
  }
~~~~~~~~

We would then expect to be able to derive a `JsDecoder` for something like our
`TradeTemplate` from Chapter 5

{lang="text"}
~~~~~~~~
  final case class TradeTemplate(
    otc: Option[Boolean] @@ Tags.Last
  )
  object TradeTemplate {
    implicit val encoder: JsEncoder[TradeTemplate] = DerivedJsEncoder.gen
  }
~~~~~~~~

But we instead get a compiler error

{lang="text"}
~~~~~~~~
  [error] could not find implicit value for parameter G: LabelledGeneric.Aux[A,R]
  [error]   implicit val encoder: JsEncoder[TradeTemplate] = DerivedJsEncoder.gen
  [error]                                                                     ^
~~~~~~~~

The error message is as helpful as always. The workaround is to introduce evidence for `H @@ Z` on the lower priority implicit scope, and then just call the code that the compiler should have found in the first place:

{lang="text"}
~~~~~~~~
  object DerivedJsEncoder extends DerivedJsEncoder1 with DerivedJsEncoder2 {
    ...
  }
  private[jsonformat] trait DerivedJsEncoder2 {
    this: DerivedJsEncoder.type =>
  
    // WORKAROUND https://github.com/milessabin/shapeless/issues/309
    implicit def hconsTagged[A, K <: Symbol, H, Z, T <: HList, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H @@ Z]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H @@ Z] :: T, None.type :: J] = hcons(K, H, T)
  
    implicit def hconsCustomTagged[A, K <: Symbol, H, Z, T <: HList, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H @@ Z]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H @@ Z] :: T, Some[json] :: J] = hconsCustom(K, H, T)
  }
~~~~~~~~

Thankfully, we only need to consider products, since coproducts cannot be tagged.


### `JsDecoder`

The decoding side is much as we can expect based on previous examples. We can
construct an instance of a `FieldType[K, H]` with the helper `field[K](h: H)`.
Supporting only the sensible defaults means we write:

{lang="text"}
~~~~~~~~
  sealed trait DerivedJsDecoder[A] {
    def fromJsObject(j: JsObject): String \/ A
  }
  object DerivedJsDecoder {
    def gen[A, R](
      implicit G: LabelledGeneric.Aux[A, R],
      R: Cached[Strict[DerivedJsDecoder[R]]]
    ): JsDecoder[A] = new JsDecoder[A] {
      def fromJson(j: JsValue) = j match {
        case o @ JsObject(_) => R.value.value.fromJsObject(o).map(G.from)
        case other           => fail("JsObject", other)
      }
    }
  
    implicit def hcons[K <: Symbol, H, T <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H]],
      T: DerivedJsDecoder[T]
    ): DerivedJsDecoder[FieldType[K, H] :: T] =
      new DerivedJsDecoder[FieldType[K, H] :: T] {
        private val fieldname = K.value.name
        def fromJsObject(j: JsObject) = {
          val value = j.get(fieldname).getOrElse(JsNull)
          for {
            head  <- H.value.fromJson(value)
            tail  <- T.fromJsObject(j)
          } yield field[K](head) :: tail
        }
      }
  
    implicit val hnil: DerivedJsDecoder[HNil] = new DerivedJsDecoder[HNil] {
      private val nil               = HNil.right[String]
      def fromJsObject(j: JsObject) = nil
    }
  
    implicit def ccons[K <: Symbol, H, T <: Coproduct](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H]],
      T: DerivedJsDecoder[T]
    ): DerivedJsDecoder[FieldType[K, H] :+: T] =
      new DerivedJsDecoder[FieldType[K, H] :+: T] {
        private val hint = ("type" -> JsString(K.value.name))
        def fromJsObject(j: JsObject) =
          if (j.fields.element(hint)) {
            j.get("xvalue")
              .into {
                case \/-(xvalue) => H.value.fromJson(xvalue)
                case -\/(_)      => H.value.fromJson(j)
              }
              .map(h => Inl(field[K](h)))
          } else
            T.fromJsObject(j).map(Inr(_))
      }
  
    implicit val cnil: DerivedJsDecoder[CNil] = new DerivedJsDecoder[CNil] {
      def fromJsObject(j: JsObject) = fail(s"JsObject with 'type' field", j)
    }
  }
~~~~~~~~

Adding user preferences via annotations follows the same route as
`DerivedJsEncoder` and is mechanical, so left as an exercise to the reader.

One final thing is missing: `case class` default values. We can request evidence
but a big problem is that we can no longer use the same derivation mechanism for
products and coproducts: the evidence is never created for coproducts.

The solution is quite drastic. We must split our `DerivedJsDecoder` into
`DerivedCoproductJsDecoder` and `DerivedProductJsDecoder`. We will focus our
attention on the `DerivedProductJsDecoder`, and while we are at it we will
use a `Map` for faster field lookup:

{lang="text"}
~~~~~~~~
  sealed trait DerivedProductJsDecoder[A, R, J <: HList, D <: HList] {
    private[jsonformat] def fromJsObject(
      j: Map[String, JsValue],
      anns: J,
      defaults: D
    ): String \/ R
  }
~~~~~~~~

We can request evidence of default values with `Default.Aux[A, D]` and duplicate
all the methods to deal with the case where we do and do not have a default
value. However, Shapeless is merciful (for once) and provides
`Default.AsOptions.Aux[A, D]` letting us handle defaults at runtime.

{lang="text"}
~~~~~~~~
  object DerivedProductJsDecoder {
    def gen[A, R, J <: HList, D <: HList](
      implicit G: LabelledGeneric.Aux[A, R],
      J: Annotations.Aux[json, A, J],
      D: Default.AsOptions.Aux[A, D],
      R: Cached[Strict[DerivedProductJsDecoder[A, R, J, D]]]
    ): JsDecoder[A] = new JsDecoder[A] {
      def fromJson(j: JsValue) = j match {
        case o @ JsObject(_) =>
          R.value.value.fromJsObject(o.fields.toMap, J(), D()).map(G.from)
        case other => fail("JsObject", other)
      }
    }
    ...
  }
~~~~~~~~

We must move the `.hcons` and `.hnil` methods onto the companion of the new
sealed typeclass, which can handle default values

{lang="text"}
~~~~~~~~
  object DerivedProductJsDecoder {
    ...
      implicit def hnil[A]: DerivedProductJsDecoder[A, HNil, HNil, HNil] =
      new DerivedProductJsDecoder[A, HNil, HNil, HNil] {
        private val nil = HNil.right[String]
        def fromJsObject(j: StringyMap[JsValue], a: HNil, defaults: HNil) = nil
      }
  
    implicit def hcons[A, K <: Symbol, H, T <: HList, J <: HList, D <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H]],
      T: DerivedProductJsDecoder[A, T, J, D]
    ): DerivedProductJsDecoder[A, FieldType[K, H] :: T, None.type :: J, Option[H] :: D] =
      new DerivedProductJsDecoder[A, FieldType[K, H] :: T, None.type :: J, Option[H] :: D] {
        private val fieldname = K.value.name
        def fromJsObject(
          j: StringyMap[JsValue],
          anns: None.type :: J,
          defaults: Option[H] :: D
        ) =
          for {
            head <- j.get(fieldname) match {
                     case Maybe.Just(v) => H.value.fromJson(v)
                     case _ =>
                       defaults.head match {
                         case Some(default) => \/-(default)
                         case None          => H.value.fromJson(JsNull)
                       }
                   }
            tail <- T.fromJsObject(j, anns.tail, defaults.tail)
          } yield field[K](head) :: tail
      }
    ...
  }
~~~~~~~~

We can no longer use `@deriving` for products and coproducts: there can only be
one entry in the `deriving.conf` file.

Oh, and don't forget to add `@@` support

{lang="text"}
~~~~~~~~
  object DerivedProductJsDecoder extends DerivedProductJsDecoder1 {
    ...
  }
  private[jsonformat] trait DerivedProductJsDecoder2 {
    this: DerivedProductJsDecoder.type =>
  
    implicit def hconsTagged[
      A, K <: Symbol, H, Z, T <: HList, J <: HList, D <: HList
    ](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H @@ Z]],
      T: DerivedProductJsDecoder[A, T, J, D]
    ): DerivedProductJsDecoder[
      A,
      FieldType[K, H @@ Z] :: T,
      None.type :: J,
      Option[H @@ Z] :: D
    ] = hcons(K, H, T)
  
    implicit def hconsCustomTagged[
      A, K <: Symbol, H, Z, T <: HList, J <: HList, D <: HList
    ](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H @@ Z]],
      T: DerivedProductJsDecoder[A, T, J, D]
    ): DerivedProductJsDecoder[
      A,
      FieldType[K, H @@ Z] :: T,
      Some[json] :: J,
      Option[H @@ Z] :: D
    ] = hconsCustomTagged(K, H, T)
  }
~~~~~~~~


### Complicated Derivations

Shapeless allows for a lot more kinds of derivations than are possible with
`scalaz-deriving` or Magnolia. As an example of an encoder / decoder that are
not possible with Magnolia, consider this XML model from [`xmlformat`](https://github.com/scalaz/scalaz-deriving/tree/master/examples/xmlformat)

{lang="text"}
~~~~~~~~
  @deriving(Equal, Show, Arbitrary)
  sealed abstract class XNode
  
  @deriving(Equal, Show, Arbitrary)
  final case class XTag(
    name: String,
    attrs: IList[XAttr],
    children: IList[XTag],
    body: Maybe[XString]
  )
  
  @deriving(Equal, Show, Arbitrary)
  final case class XAttr(name: String, value: XString)
  
  @deriving(Show)
  @xderiving(Equal, Monoid, Arbitrary)
  final case class XChildren(tree: IList[XTag]) extends XNode
  
  @deriving(Show)
  @xderiving(Equal, Semigroup, Arbitrary)
  final case class XString(text: String) extends XNode
~~~~~~~~

Given the nature of XML it makes sense to have separate encoder / decoder pairs
for `XChildren` and `XString` content. We could provide a derivation for the
`XChildren` with Shapeless but we want to special case fields based on the kind
of typeclass they have, as well as `Option` fields. We could even require that
fields are annotated with their encoded name. In addition, when decoding we wish
to have different strategies for handling XML element bodies, which can be
multipart, depending on if our type has a `Semigroup`, `Monoid` or neither.

A> Many developers believe XML is simply a more verbose form of JSON, with angle
A> brackets instead of curlies. However, an attempt to write a round trip converter
A> between `XNode` and `JsValue` should convince us that JSON and XML are different
A> species, with conversions only possible on a case-by-case basis.


### Example: `UrlQueryWriter`

Along similar lines as `xmlformat`, our `drone-dynamic-agents` application could
benefit from a typeclass derivation of the `UrlQueryWriter` typeclass, which is
built out of `UrlEncodedWriter` instances for each field entry. It does not
support coproducts:

{lang="text"}
~~~~~~~~
  @typeclass trait UrlQueryWriter[A] {
    def toUrlQuery(a: A): UrlQuery
  }
  trait DerivedUrlQueryWriter[T] extends UrlQueryWriter[T]
  object DerivedUrlQueryWriter {
    def gen[T, Repr](
      implicit
      G: LabelledGeneric.Aux[T, Repr],
      CR: Cached[Strict[DerivedUrlQueryWriter[Repr]]]
    ): UrlQueryWriter[T] = { t =>
      CR.value.value.toUrlQuery(G.to(t))
    }
  
    implicit val hnil: DerivedUrlQueryWriter[HNil] = { _ =>
      UrlQuery(IList.empty)
    }
    implicit def hcons[Key <: Symbol, A, Remaining <: HList](
      implicit Key: Witness.Aux[Key],
      LV: Lazy[UrlEncodedWriter[A]],
      DR: DerivedUrlQueryWriter[Remaining]
    ): DerivedUrlQueryWriter[FieldType[Key, A] :: Remaining] = {
      case head :: tail =>
        val first =
          Key.value.name -> URLDecoder.decode(LV.value.toUrlEncoded(head).value, "UTF-8")
        val rest = DR.toUrlQuery(tail)
        UrlQuery(first :: rest.params)
    }
  }
~~~~~~~~

It is reasonable to ask if these 30 lines are actually an improvement over the 8
lines for the 2 manual instances our application needs: a decision to be taken
on a case by case basis.

For completeness, the `UrlEncodedWriter` derivation can be written with Magnolia

{lang="text"}
~~~~~~~~
  object UrlEncodedWriterMagnolia {
    type Typeclass[a] = UrlEncodedWriter[a]
    def combine[A](ctx: CaseClass[UrlEncodedWriter, A]) = a =>
      Refined.unsafeApply(ctx.parameters.map { p =>
        p.label + "=" + p.typeclass.toUrlEncoded(p.dereference(a))
      }.toList.intercalate("&"))
    def gen[A]: UrlEncodedWriter[A] = macro Magnolia.gen[A]
  }
~~~~~~~~


### The Dark Side of Derivation

> "Beware fully automatic derivation. Anger, fear, aggression; the dark side of
> the derivation are they. Easily they flow, quick to join you in a fight. If once
> you start down the dark path, forever will it dominate your compiler, consume
> you it will."
> 
> ― an ancient Shapeless master

In addition to all the warnings about fully automatic derivation that were
mentioned for Magnolia, Shapeless is **much** worse. Not only is fully automatic
Shapeless derivation [the most common cause of slow compiles](https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html), it is also a
painful source of typeclass coherence bugs.

Fully automatic derivation is when the `def gen` are `implicit` such that a call
will recurse for all entries in the ADT. Because of the way that implicit scopes
work, an imported `implicit def` will have a higher priority than custom
instances on companions, creating a source of typeclass decoherence. For
example, consider this code if our `.gen` were implicit

{lang="text"}
~~~~~~~~
  import DerivedJsEncoder._
  
  @xderiving(JsEncoder)
  final case class Foo(s: String)
  final case class Bar(foo: Foo)
~~~~~~~~

We might expect the full-auto encoded form of `Bar("hello")` to look like

{lang="text"}
~~~~~~~~
  {
    "foo":"hello"
  }
~~~~~~~~

because we have used `xderiving` for `Foo`. But it can instead be

{lang="text"}
~~~~~~~~
  {
    "foo": {
      "s":"hello"
    }
  }
~~~~~~~~

Worse yet is when implicit methods are added to the companion of the typeclass,
meaning that the typeclass is always derived at the point of use and users are
unable opt out.

Fundamentally, when writing generic programs, implicits can be ignored by the
compiler depending on scope, meaning that we lose the compiletime safety that
was our motivation for programming at the type level in the first place!

Everything is much simpler in the light side, where `implicit` is only used for
coherent, globally unique, typeclasses. Fear of boilerplate is the path to the
dark side. Fear leads to anger. Anger leads to hate. Hate leads to suffering.


## Performance

There is no silver bullet when it comes to typeclass derivation. An axis to
consider is performance: both at compiletime and runtime.


#### Compile Times

When it comes to compilation times, Shapeless is the outlier. It is not uncommon
to see a small project expand from a one second compile to a one minute compile.
To investigate compilation issues, we can profile our applications with the
`scalac-profiling` plugin

{lang="text"}
~~~~~~~~
  addCompilerPlugin("ch.epfl.scala" %% "scalac-profiling" % "1.0.0")
  scalacOptions ++= Seq("-Ystatistics:typer", "-P:scalac-profiling:no-profiledb")
~~~~~~~~

It produces output that can generate a *flame graph*.

For a typical Shapeless derivation, we get a lively chart

{width=90%}
![](images/implicit-flamegraph-jsonformat-jmh.png)

almost the entire compile time is spent in implicit resolution. Note that this
also includes compiling the `scalaz-deriving`, Magnolia and manual instances,
but the Shapeless computations dominate.

And this is when it works. If there is a problem with a shapeless derivation,
the compiler can get stuck in an infinite loop and must be killed.


#### Runtime Performance

If we move to runtime performance, the answer is always *it depends*.

Assuming that the derivation logic has been written in an efficient way, it is
only possible to know which is faster through experimentation.

The `jsonformat` library uses the [Java Microbenchmark Harness (JMH)](http://openjdk.java.net/projects/code-tools/jmh/) on models
that map to GeoJSON, Google Maps, and Twitter, contributed by Andriy
Plokhotnyuk. There are three tests per model:

-   encoding the `ADT` to a `JsValue`
-   a successful decoding of the same `JsValue` back into an ADT
-   a failure decoding of a `JsValue` with a data error

applied to the following implementations:

-   Magnolia
-   Shapeless
-   manually written

with the equivalent optimisations in each. The results are in operations per
second (higher is better), on a powerful desktop computer, using a single
thread:

{lang="text"}
~~~~~~~~
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*encode*
  Benchmark                                 Mode  Cnt       Score      Error  Units
  
  GeoJSONBenchmarks.encodeMagnolia         thrpt    5   70527.223 ±  546.991  ops/s
  GeoJSONBenchmarks.encodeShapeless        thrpt    5   65925.215 ±  309.623  ops/s
  GeoJSONBenchmarks.encodeManual           thrpt    5   96435.691 ±  334.652  ops/s
  
  GoogleMapsAPIBenchmarks.encodeMagnolia   thrpt    5   73107.747 ±  439.803  ops/s
  GoogleMapsAPIBenchmarks.encodeShapeless  thrpt    5   53867.845 ±  510.888  ops/s
  GoogleMapsAPIBenchmarks.encodeManual     thrpt    5  127608.402 ± 1584.038  ops/s
  
  TwitterAPIBenchmarks.encodeMagnolia      thrpt    5  133425.164 ± 1281.331  ops/s
  TwitterAPIBenchmarks.encodeShapeless     thrpt    5   84233.065 ±  352.611  ops/s
  TwitterAPIBenchmarks.encodeManual        thrpt    5  281606.574 ± 1975.873  ops/s
~~~~~~~~

We see that the manual implementations are in the lead, followed by Magnolia,
with Shapeless from 30% to 70% the performance of the manual instances. Now for
decoding

{lang="text"}
~~~~~~~~
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*decode.*Success
  Benchmark                                        Mode  Cnt       Score      Error  Units
  
  GeoJSONBenchmarks.decodeMagnoliaSuccess         thrpt    5   40850.270 ±  201.457  ops/s
  GeoJSONBenchmarks.decodeShapelessSuccess        thrpt    5   41173.199 ±  373.048  ops/s
  GeoJSONBenchmarks.decodeManualSuccess           thrpt    5  110961.246 ±  468.384  ops/s
  
  GoogleMapsAPIBenchmarks.decodeMagnoliaSuccess   thrpt    5   44577.796 ±  457.861  ops/s
  GoogleMapsAPIBenchmarks.decodeShapelessSuccess  thrpt    5   31649.792 ±  861.169  ops/s
  GoogleMapsAPIBenchmarks.decodeManualSuccess     thrpt    5   56250.913 ±  394.105  ops/s
  
  TwitterAPIBenchmarks.decodeMagnoliaSuccess      thrpt    5   55868.832 ± 1106.543  ops/s
  TwitterAPIBenchmarks.decodeShapelessSuccess     thrpt    5   47711.161 ±  356.911  ops/s
  TwitterAPIBenchmarks.decodeManualSuccess        thrpt    5   71962.394 ±  465.752  ops/s
~~~~~~~~

This is a tighter race for second place, with Shapeless and Magnolia keeping
pace. Finally, decoding from a `JsValue` that contains invalid data (in an
intentionally awkward position)

{lang="text"}
~~~~~~~~
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*decode.*Error
  Benchmark                                      Mode  Cnt        Score       Error  Units
  
  GeoJSONBenchmarks.decodeMagnoliaError         thrpt    5   981094.831 ± 11051.370  ops/s
  GeoJSONBenchmarks.decodeShapelessError        thrpt    5   816704.635 ±  9781.467  ops/s
  GeoJSONBenchmarks.decodeManualError           thrpt    5   586733.762 ±  6389.296  ops/s
  
  GoogleMapsAPIBenchmarks.decodeMagnoliaError   thrpt    5  1288888.446 ± 11091.080  ops/s
  GoogleMapsAPIBenchmarks.decodeShapelessError  thrpt    5  1010145.363 ±  9448.110  ops/s
  GoogleMapsAPIBenchmarks.decodeManualError     thrpt    5  1417662.720 ±  1197.283  ops/s
  
  TwitterAPIBenchmarks.decodeMagnoliaError      thrpt    5   128704.299 ±   832.122  ops/s
  TwitterAPIBenchmarks.decodeShapelessError     thrpt    5   109715.865 ±   826.488  ops/s
  TwitterAPIBenchmarks.decodeManualError        thrpt    5   148814.730 ±  1105.316  ops/s
~~~~~~~~

Just when we thought we were seeing a pattern, both Magnolia and Shapeless win
the race when decoding invalid GeoJSON data, but manual instances win the Google
Maps and Twitter challenges.

We want to include `scalaz-deriving` in the comparison, so we compare an
equivalent implementation of `Equal`, tested on two values that contain the same
contents (`True`) and two values that contain slightly different contents
(`False`)

{lang="text"}
~~~~~~~~
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*equal*
  Benchmark                                     Mode  Cnt        Score       Error  Units
  
  GeoJSONBenchmarks.equalScalazTrue            thrpt    5   276851.493 ±  1776.428  ops/s
  GeoJSONBenchmarks.equalMagnoliaTrue          thrpt    5    93106.945 ±  1051.062  ops/s
  GeoJSONBenchmarks.equalShapelessTrue         thrpt    5   266633.522 ±  4972.167  ops/s
  GeoJSONBenchmarks.equalManualTrue            thrpt    5   599219.169 ±  8331.308  ops/s
  
  GoogleMapsAPIBenchmarks.equalScalazTrue      thrpt    5    35442.577 ±   281.597  ops/s
  GoogleMapsAPIBenchmarks.equalMagnoliaTrue    thrpt    5    91016.557 ±   688.308  ops/s
  GoogleMapsAPIBenchmarks.equalShapelessTrue   thrpt    5   107245.505 ±   468.427  ops/s
  GoogleMapsAPIBenchmarks.equalManualTrue      thrpt    5   302247.760 ±  1927.858  ops/s
  
  TwitterAPIBenchmarks.equalScalazTrue         thrpt    5    99066.013 ±  1125.422  ops/s
  TwitterAPIBenchmarks.equalMagnoliaTrue       thrpt    5   236289.706 ±  3182.664  ops/s
  TwitterAPIBenchmarks.equalShapelessTrue      thrpt    5   251578.931 ±  2430.738  ops/s
  TwitterAPIBenchmarks.equalManualTrue         thrpt    5   865845.158 ±  6339.379  ops/s
~~~~~~~~

As expected, the manual instances are far ahead of the crowd, with Shapeless
mostly leading the automatic derivations. `scalaz-deriving` makes a great effort
for GeoJSON but falls far behind in both the Google Maps and Twitter tests. The
`False` tests are more of the same:

{lang="text"}
~~~~~~~~
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*equal*
  Benchmark                                     Mode  Cnt        Score       Error  Units
  
  GeoJSONBenchmarks.equalScalazFalse           thrpt    5    89552.875 ±   821.791  ops/s
  GeoJSONBenchmarks.equalMagnoliaFalse         thrpt    5    86044.021 ±  7790.350  ops/s
  GeoJSONBenchmarks.equalShapelessFalse        thrpt    5   262979.062 ±  3310.750  ops/s
  GeoJSONBenchmarks.equalManualFalse           thrpt    5   599989.203 ± 23727.672  ops/s
  
  GoogleMapsAPIBenchmarks.equalScalazFalse     thrpt    5    35970.818 ±   288.609  ops/s
  GoogleMapsAPIBenchmarks.equalMagnoliaFalse   thrpt    5    82381.975 ±   625.407  ops/s
  GoogleMapsAPIBenchmarks.equalShapelessFalse  thrpt    5   110721.122 ±   579.331  ops/s
  GoogleMapsAPIBenchmarks.equalManualFalse     thrpt    5   303588.815 ±  2562.747  ops/s
  
  TwitterAPIBenchmarks.equalScalazFalse        thrpt    5   193930.568 ±  1176.421  ops/s
  TwitterAPIBenchmarks.equalMagnoliaFalse      thrpt    5   429764.654 ± 11944.057  ops/s
  TwitterAPIBenchmarks.equalShapelessFalse     thrpt    5   494510.588 ±  1455.647  ops/s
  TwitterAPIBenchmarks.equalManualFalse        thrpt    5  1631964.531 ± 13110.291  ops/s
~~~~~~~~

The runtime performance of `scalaz-deriving`, Magnolia and Shapeless is usually
good enough. We should be realistic: we are not writing applications that need to
be able to encode more than 130,000 values to JSON, per second, on a single
core, on the JVM. If that is a problem, look into C++.

It is unlikely that derived instances will be an application's bottleneck. Even
if it is, there is the manually written escape hatch, which is more powerful and
therefore more dangerous: it is easy to introduce typos, bugs, and even
performance regressions by accident when writing a manual instance.

In conclusion: hokey derivations and ancient macros are no match for a good hand
written instance at your side, kid.

A> We could spend a lifetime with the [`async-profiler`](https://github.com/jvm-profiling-tools/async-profiler) investigating CPU and object
A> allocation flame graphs to make any of these implementations faster. For
A> example, there are some optimisations in the actual `jsonformat` codebase not
A> reproduced here, such as a more optimised `JsObject` field lookup, and inclusion
A> of `.xmap`, `.map` and `.contramap` on the relevant typeclasses, but it is fair
A> to say that the codebase primarily focuses on readability over optimisation and
A> still achieves incredible performance.


## Summary

When deciding on a technology to use for typeclass derivation, this feature
chart may help:

| Feature        | Scalaz | Magnolia | Shapeless    | Manual       |
|-------------- |------ |-------- |------------ |------------ |
| `@deriving`    | yes    | yes      | yes          |              |
| Laws           | yes    |          |              |              |
| Fast compiles  | yes    | yes      |              | yes          |
| Field names    |        | yes      | yes          |              |
| Annotations    |        | yes      | partially    |              |
| Default values |        | yes      | with caveats |              |
| Complicated    |        |          | painfully so |              |
| Performance    |        |          |              | hold my beer |

Prefer `scalaz-deriving` if possible, using Magnolia for encoders / decoders or
if performance is a larger concern, escalating to Shapeless for complicated
derivations only if compilation times are not a concern.

Manual instances are always an escape hatch for special cases and to achieve the
ultimate performance. Avoid introducing typo bugs with manual instances by using
a code generation tool.


# Wiring up the Application

To finish, we will apply what we have learnt to wire up the example application,
and implement an HTTP client and server using the [http4s](https://http4s.org/) pure FP library.

The source code to the `drone-dynamic-agents` application is available along
with the book's source code at `https://github.com/fommil/fpmortals` under the
`examples` folder. It is not necessary to be at a computer to read this chapter,
but many readers may prefer to explore the codebase in addition to this text.

Some parts of the application have been left unimplemented, as an exercise to
the reader. See the `README` for further instructions.


## Overview

Our main application only requires an implementation of the `DynAgents` algebra.

{lang="text"}
~~~~~~~~
  trait DynAgents[F[_]] {
    def initial: F[WorldView]
    def update(old: WorldView): F[WorldView]
    def act(world: WorldView): F[WorldView]
  }
~~~~~~~~

We have an implementation already, `DynAgentsModule`, which requires
implementations of the `Drone` and `Machines` algebras, which require a
`JsonClient`, `LocalClock` and OAuth2 algebras, etc, etc, etc.

It is helpful to get a complete picture of all the algebras, modules and
interpreters of the application. This is the layout of the source code:

{lang="text"}
~~~~~~~~
  ├── dda
  │   ├── algebra.scala
  │   ├── DynAgents.scala
  │   ├── main.scala
  │   └── interpreters
  │       ├── DroneModule.scala
  │       └── GoogleMachinesModule.scala
  ├── http
  │   ├── JsonClient.scala
  │   ├── OAuth2JsonClient.scala
  │   ├── encoding
  │   │   ├── UrlEncoded.scala
  │   │   ├── UrlEncodedWriter.scala
  │   │   ├── UrlQuery.scala
  │   │   └── UrlQueryWriter.scala
  │   ├── oauth2
  │   │   ├── Access.scala
  │   │   ├── Auth.scala
  │   │   ├── Refresh.scala
  │   │   └── interpreters
  │   │       └── BlazeUserInteraction.scala
  │   └── interpreters
  │       └── BlazeJsonClient.scala
  ├── os
  │   └── Browser.scala
  └── time
      ├── Epoch.scala
      ├── LocalClock.scala
      └── Sleep.scala
~~~~~~~~

The signatures of all the algebras can be summarised as

{lang="text"}
~~~~~~~~
  trait Sleep[F[_]] {
    def sleep(time: FiniteDuration): F[Unit]
  }
  
  trait LocalClock[F[_]] {
    def now: F[Epoch]
  }
  
  trait JsonClient[F[_]] {
    def get[A: JsDecoder](
      uri: String Refined Url,
      headers: IList[(String, String)]
    ): F[A]
  
    def post[P: UrlEncodedWriter, A: JsDecoder](
      uri: String Refined Url,
      payload: P,
      headers: IList[(String, String)]
    ): F[A]
  }
  
  trait Auth[F[_]] {
    def authenticate: F[CodeToken]
  }
  trait Access[F[_]] {
    def access(code: CodeToken): F[(RefreshToken, BearerToken)]
  }
  trait Refresh[F[_]] {
    def bearer(refresh: RefreshToken): F[BearerToken]
  }
  trait OAuth2JsonClient[F[_]] {
    // same methods as JsonClient, but doing OAuth2 transparently
  }
  
  trait UserInteraction[F[_]] {
    def start: F[String Refined Url]
    def open(uri: String Refined Url): F[Unit]
    def stop: F[CodeToken]
  }
  
  trait Drone[F[_]] {
    def getBacklog: F[Int]
    def getAgents: F[Int]
  }
  
  trait Machines[F[_]] {
    def getTime: F[Epoch]
    def getManaged: F[NonEmptyList[MachineNode]]
    def getAlive: F[MachineNode ==>> Epoch]
    def start(node: MachineNode): F[Unit]
    def stop(node: MachineNode): F[Unit]
  }
~~~~~~~~

Note that some signatures from previous chapters have been refactored to use
Scalaz data types, now that we know why they are superior to the stdlib.

The data types are:

{lang="text"}
~~~~~~~~
  @xderiving(Order, Arbitrary)
  final case class Epoch(millis: Long) extends AnyVal
  
  @deriving(Order, Show)
  final case class MachineNode(id: String)
  
  @deriving(Equal, Show)
  final case class CodeToken(token: String, redirect_uri: String Refined Url)
  
  @xderiving(Equal, Show, ConfigReader)
  final case class RefreshToken(token: String) extends AnyVal
  
  @deriving(Equal, Show, ConfigReader)
  final case class BearerToken(token: String, expires: Epoch)
  
  @deriving(ConfigReader)
  final case class OAuth2Config(token: RefreshToken, server: ServerConfig)
  
  @deriving(ConfigReader)
  final case class AppConfig(drone: BearerToken, machines: OAuth2Config)
  
  @xderiving(UrlEncodedWriter)
  final case class UrlQuery(params: IList[(String, String)]) extends AnyVal
~~~~~~~~

and the typeclasses are

{lang="text"}
~~~~~~~~
  @typeclass trait UrlEncodedWriter[A] {
    def toUrlEncoded(a: A): String Refined UrlEncoded
  }
  @typeclass trait UrlQueryWriter[A] {
    def toUrlQuery(a: A): UrlQuery
  }
~~~~~~~~

We derive useful typeclasses using `scalaz-deriving` and Magnolia. The
`ConfigReader` typeclass is from the `pureconfig` library and is used to read
runtime configuration from HOCON property files.

And without going into the detail of how to implement the algebras, we need to
know the dependency graph of our `DynAgentsModule`.

{lang="text"}
~~~~~~~~
  final class DynAgentsModule[F[_]: Applicative](
    D: Drone[F],
    M: Machines[F]
  ) extends DynAgents[F] { ... }
  
  final class DroneModule[F[_]](
    H: OAuth2JsonClient[F]
  ) extends Drone[F] { ... }
  
  final class GoogleMachinesModule[F[_]](
    H: OAuth2JsonClient[F]
  ) extends Machines[F] { ... }
~~~~~~~~

There are two modules implementing `OAuth2JsonClient`, one that will use the OAuth2 `Refresh` algebra (for Google) and another that reuses a non-expiring `BearerToken` (for Drone).

{lang="text"}
~~~~~~~~
  final class OAuth2JsonClientModule[F[_]](
    token: RefreshToken
  )(
    H: JsonClient[F],
    T: LocalClock[F],
    A: Refresh[F]
  )(
    implicit F: MonadState[F, BearerToken]
  ) extends OAuth2JsonClient[F] { ... }
  
  final class BearerJsonClientModule[F[_]: Monad](
    bearer: BearerToken
  )(
    H: JsonClient[F]
  ) extends OAuth2JsonClient[F] { ... }
~~~~~~~~

So far we have seen requirements for `F` to have an `Applicative[F]`, `Monad[F]`
and `MonadState[F, BearerToken]`. All of these requirements can be satisfied by
using `StateT[Task, BearerToken, ?]` as our application's context.

However, some of our algebras only have one interpreter, using `Task`

{lang="text"}
~~~~~~~~
  final class LocalClockTask extends LocalClock[Task] { ... }
  final class SleepTask extends Sleep[Task] { ... }
~~~~~~~~

But recall that our algebras can provide a `liftM` on their companion, see
Chapter 7.4 on the Monad Transformer Library, allowing us to lift a
`LocalClock[Task]` into our desired `StateT[Task, BearerToken, ?]` context, and
everything is consistent.

Unfortunately, that is not the end of the story. Things get more complicated
when we go to the next layer. Our `JsonClient` has an interpreter using a
different context

{lang="text"}
~~~~~~~~
  final class BlazeJsonClient[F[_]](H: Client[Task])(
    implicit
    F: MonadError[F, JsonClient.Error],
    I: MonadIO[F, Throwable]
  ) extends JsonClient[F] { ... }
  object BlazeJsonClient {
    def apply[F[_]](
      implicit
      F: MonadError[F, JsonClient.Error],
      I: MonadIO[F, Throwable]
    ): Task[JsonClient[F]] = ...
  }
~~~~~~~~

Note that the `BlazeJsonClient` constructor returns a `Task[JsonClient[F]]`, not
a `JsonClient[F]`. This is because the act of creating the client is effectful:
mutable connection pools are created and managed internally by http4s.

A> `OAuth2JsonClientModule` requires a `MonadState` and `BlazeJsonClient` requires
A> `MonadError` and `MonadIO`. Our application's context will now likely be the
A> combination of both:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   StateT[EitherT[Task, JsonClient.Error, ?], BearerToken, ?]
A> ~~~~~~~~
A> 
A> A monad stack. Monad stacks automatically provide appropriate instances of
A> `MonadState` and `MonadError` when nested, so we don't need to think about it.
A> If we had hard-coded the implementation in the interpreter, and returned an
A> `EitherT[Task, Error, ?]` from `BlazeJsonClient`, it would make it a lot harder
A> to instantiate.

We must not forget that we need to provide a `RefreshToken` for
`GoogleMachinesModule`. We could ask the user to do all the legwork, but we are
nice and provide a separate one-shot application that uses the `Auth` and
`Access` algebras. The `AuthModule` and `AccessModule` implementations bring in
additional dependencies, but thankfully no change to the application's `F[_]`
context.

{lang="text"}
~~~~~~~~
  final class AuthModule[F[_]: Monad](
    config: ServerConfig
  )(
    I: UserInteraction[F]
  ) extends Auth[F] { ... }
  
  final class AccessModule[F[_]: Monad](
    config: ServerConfig
  )(
    H: JsonClient[F],
    T: LocalClock[F]
  ) extends Access[F] { ... }
  
  final class BlazeUserInteraction private (
    pserver: Promise[Void, Server[Task]],
    ptoken: Promise[Void, String]
  ) extends UserInteraction[Task] { ... }
  object BlazeUserInteraction {
    def apply(): Task[BlazeUserInteraction] = ...
  }
~~~~~~~~

The interpreter for `UserInteraction` is the most complex part of our codebase:
it starts an HTTP server, sends the user to visit a webpage in their browser,
captures a callback in the server, and then returns the result while safely
shutting down the web server.

Rather than using a `StateT` to manage this state, we use a `Promise` primitive
(from `ioeffect`). We should always use `Promise` (or `IORef`) instead of a
`StateT` when we are writing an `IO` interpreter since it allows us to contain
the abstraction. If we were to use a `StateT`, not only would it have a
performance impact on the entire application, but it would also leak internal
state management to the main application, which would become responsible for
providing the initial value. We also couldn't use `StateT` in this scenario
because we need "wait for" semantics that are only provided by `Promise`.


## `Main`

The ugliest part of FP is making sure that monads are all aligned and this tends
to happen in the `Main` entrypoint.

Our main loop is

{lang="text"}
~~~~~~~~
  state = initial()
  while True:
    state = update(state)
    state = act(state)
~~~~~~~~

and the good news is that the actual code will look like

{lang="text"}
~~~~~~~~
  for {
    old     <- F.get
    updated <- A.update(old)
    changed <- A.act(updated)
    _       <- F.put(changed)
    _       <- S.sleep(10.seconds)
  } yield ()
~~~~~~~~

where `F` holds the state of the world in a `MonadState[F, WorldView]`. We can
put this into a method called `.step` and repeat it forever by calling
`.step[F].forever[Unit]`.

There are two approaches we can take, and we will explore both. The first, and
simplest, is to construct one monad stack that all algebras are compatible with.
Everything gets a `.liftM` added to it to lift it into the larger stack.

The code we want to write for the one-shot authentication mode is

{lang="text"}
~~~~~~~~
  def auth(name: String): Task[Unit] = {
    for {
      config    <- readConfig[ServerConfig](name + ".server")
      ui        <- BlazeUserInteraction()
      auth      = new AuthModule(config)(ui)
      codetoken <- auth.authenticate
      client    <- BlazeJsonClient
      clock     = new LocalClockTask
      access    = new AccessModule(config)(client, clock)
      token     <- access.access(codetoken)
      _         <- putStrLn(s"got token: $token")
    } yield ()
  }.run
~~~~~~~~

where `.readConfig` and `.putStrLn` are library calls. We can think of them as
`Task` interpreters of algebras that read the application's runtime
configuration and print a string to the screen.

But this code does not compile, for two reasons. Firstly, we need to consider
what our monad stack is going to be. The `BlazeJsonClient` constructor returns a
`Task` but the `JsonClient` methods require a `MonadError[...,
JsonClient.Error]`. This can be provided by `EitherT`. We can therefore
construct the common monad stack for the entire `for` comprehension as

{lang="text"}
~~~~~~~~
  type H[a] = EitherT[Task, JsonClient.Error, a]
~~~~~~~~

Unfortunately this means we must `.liftM` everything that returns a `Task`,
which adds quite a lot of boilerplate. Unfortunately, the `.liftM` method does
not take a type of shape `H[_]`, it takes a type of shape `H[_[_], _]`, so we
need to create a type alias to help out the compiler:

{lang="text"}
~~~~~~~~
  type HT[f[_], a] = EitherT[f, JsonClient.Error, a]
  type H[a]        = HT[Task, a]
~~~~~~~~

we can now call `.liftM[HT]` when we receive a `Task`

{lang="text"}
~~~~~~~~
  for {
    config    <- readConfig[ServerConfig](name + ".server").liftM[HT]
    ui        <- BlazeUserInteraction().liftM[HT]
    auth      = new AuthModule(config)(ui)
    codetoken <- auth.authenticate.liftM[HT]
    client    <- BlazeJsonClient[H].liftM[HT]
    clock     = new LocalClockTask
    access    = new AccessModule(config)(client, clock)
    token     <- access.access(codetoken)
    _         <- putStrLn(s"got token: $token").liftM[HT]
  } yield ()
~~~~~~~~

But this still doesn't compile, because `clock` is a `LocalClock[Task]` and `AccessModule` requires a `LocalClock[H]`. We simply add the necessary `.liftM` boilerplate to the companion of `LocalClock` and can then lift the entire algebra

{lang="text"}
~~~~~~~~
  clock     = LocalClock.liftM[Task, HT](new LocalClockTask)
~~~~~~~~

and now everything compiles!

The second approach to wiring up an application is more complex, but necessary
when there are conflicts in the monad stack, such as we need in our main loop.
If we perform an analysis we find that the following are needed:

-   `MonadError[F, JsonClient.Error]` for uses of the `JsonClient`
-   `MonadState[F, BearerToken]` for uses of the `OAuth2JsonClient`
-   `MonadState[F, WorldView]` for our main loop

Unfortunately, the two `MonadState` requirements are in conflict. We could
construct a data type that captures all the state of the program, but that is a
leaky abstraction. Instead, we nest our `for` comprehensions and provide state
where it is needed.

We now need to think about three layers, which we will call `F`, `G`, `H`

{lang="text"}
~~~~~~~~
  type HT[f[_], a] = EitherT[f, JsonClient.Error, a]
  type GT[f[_], a] = StateT[f, BearerToken, a]
  type FT[f[_], a] = StateT[f, WorldView, a]
  
  type H[a]        = HT[Task, a]
  type G[a]        = GT[H, a]
  type F[a]        = FT[G, a]
~~~~~~~~

Now some bad news about `.liftM`... it only works for one layer at a time. If we
have a `Task[A]` and we want an `F[A]`, we have to go through each step and type
`ta.liftM[HT].liftM[GT].liftM[FT]`. Likewise, when lifting algebras we have to
call `liftM` multiple times. To get a `Sleep[F]`, we have to type

{lang="text"}
~~~~~~~~
  val S: Sleep[F] = {
    import Sleep.liftM
    liftM(liftM(liftM(new SleepTask)))
  }
~~~~~~~~

and to get a `LocalClock[G]` we do two lifts

{lang="text"}
~~~~~~~~
  val T: LocalClock[G] = {
    import LocalClock.liftM
    liftM(liftM(new LocalClockTask))
  }
~~~~~~~~

The main application then becomes

{lang="text"}
~~~~~~~~
  def agents(bearer: BearerToken): Task[Unit] = {
    ...
    for {
      config <- readConfig[AppConfig]
      blaze  <- BlazeJsonClient[G]
      _ <- {
        val bearerClient = new BearerJsonClientModule(bearer)(blaze)
        val drone        = new DroneModule(bearerClient)
        val refresh      = new RefreshModule(config.machines.server)(blaze, T)
        val oauthClient =
          new OAuth2JsonClientModule(config.machines.token)(blaze, T, refresh)
        val machines = new GoogleMachinesModule(oauthClient)
        val agents   = new DynAgentsModule(drone, machines)
        for {
          start <- agents.initial
          _ <- {
            val fagents = DynAgents.liftM[G, FT](agents)
            step(fagents, S).forever[Unit]
          }.run(start)
        } yield ()
      }.eval(bearer).run
    } yield ()
  }
~~~~~~~~

where the outer loop is using `Task`, the middle loop is using `G`, and the
inner loop is using `F`.

The calls to `.run(start)` and `.eval(bearer)` are where we provide the initial
state for the `StateT` parts of our application. The `.run` is to reveal the
`EitherT` error.

We can call these two application entry points from our `SafeApp`

{lang="text"}
~~~~~~~~
  object Main extends SafeApp {
    def run(args: List[String]): IO[Void, ExitStatus] = {
      if (args.contains("--machines")) auth("machines")
      else agents(BearerToken("<invalid>", Epoch(0)))
    }.attempt[Void].map {
      case \/-(_)   => ExitStatus.ExitNow(0)
      case -\/(err) => ExitStatus.ExitNow(1)
    }
  }
~~~~~~~~

and then run it!

{lang="text"}
~~~~~~~~
  > runMain fommil.dda.Main --machines
  [info] Running (fork) fommil.dda.Main --machines
  ...
  [info] Service bound to address /127.0.0.1:46687
  ...
  [info] Created new window in existing browser session.
  ...
  [info] Headers(Host: localhost:46687, Connection: keep-alive, User-Agent: Mozilla/5.0 ...)
  ...
  [info] POST https://www.googleapis.com/oauth2/v4/token
  ...
  [info] got token: "<elided>"
~~~~~~~~

Yay!


## Blaze

We implement the HTTP client and server with the third party library `http4s`.
The interpreters for their client and server algebras are called *Blaze*.

We need the following dependencies

{lang="text"}
~~~~~~~~
  val http4sVersion = "0.18.16"
  libraryDependencies ++= Seq(
    "org.http4s"            %% "http4s-dsl"          % http4sVersion,
    "org.http4s"            %% "http4s-blaze-server" % http4sVersion,
    "org.http4s"            %% "http4s-blaze-client" % http4sVersion
  )
~~~~~~~~


### `BlazeJsonClient`

We will need some imports

{lang="text"}
~~~~~~~~
  import org.http4s
  import org.http4s.{ EntityEncoder, MediaType }
  import org.http4s.headers.`Content-Type`
  import org.http4s.client.Client
  import org.http4s.client.blaze.{ BlazeClientConfig, Http1Client }
~~~~~~~~

The `Client` module can be summarised as

{lang="text"}
~~~~~~~~
  final class Client[F[_]](
    val shutdown: F[Unit]
  )(implicit F: MonadError[F, Throwable]) {
    def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A] = ...
    ...
  }
~~~~~~~~

where `Request` and `Response` are data types:

{lang="text"}
~~~~~~~~
  final case class Request[F[_]](
    method: Method
    uri: Uri,
    headers: Headers,
    body: EntityBody[F]
  ) {
    def withBody[A](a: A)
                   (implicit F: Monad[F], A: EntityEncoder[F, A]): F[Request[F]] = ...
    ...
  }
  
  final case class Response[F[_]](
    status: Status,
    headers: Headers,
    body: EntityBody[F]
  )
~~~~~~~~

made of

{lang="text"}
~~~~~~~~
  final case class Headers(headers: List[Header])
  final case class Header(name: String, value: String)
  
  final case class Uri( ... )
  object Uri {
    // not total, only use if `s` is guaranteed to be a URL
    def unsafeFromString(s: String): Uri = ...
    ...
  }
  
  final case class Status(code: Int) {
    def isSuccess: Boolean = ...
    ...
  }
  
  type EntityBody[F[_]] = fs2.Stream[F, Byte]
~~~~~~~~

The `EntityBody` type is an alias to `Stream` from the [`fs2`](https://github.com/functional-streams-for-scala/fs2) library. The
`Stream` data type can be thought of as an effectful, lazy, pull-based stream of
data. It is implemented as a `Free` monad with exception catching and
interruption. `Stream` takes two type parameters: an effect type and a content
type, and has an efficient internal representation for batching the data. For
example, although we are using `Stream[F, Byte]`, it is actually wrapping the
raw `Array[Byte]` that arrives over the network.

We need to convert our header and URL representations into the versions required
by http4s:

{lang="text"}
~~~~~~~~
  def convert(headers: IList[(String, String)]): http4s.Headers =
    http4s.Headers(
      headers.foldRight(List[http4s.Header]()) {
        case ((key, value), acc) => http4s.Header(key, value) :: acc
      }
    )
  
  def convert(uri: String Refined Url): http4s.Uri =
    http4s.Uri.unsafeFromString(uri.value) // we already validated our String
~~~~~~~~

Both our `.get` and `.post` methods require a conversion from the http4s
`Response` type into an `A`. We can factor this out into a single function,
`.handler`

{lang="text"}
~~~~~~~~
  import JsonClient.Error
  
  final class BlazeJsonClient[F[_]] private (H: Client[Task])(
    implicit
    F: MonadError[F, Error],
    I: MonadIO[F, Throwable]
  ) extends JsonClient[F] {
    ...
    def handler[A: JsDecoder](resp: http4s.Response[Task]): Task[Error \/ A] = {
      if (!resp.status.isSuccess)
        Task.now(JsonClient.ServerError(resp.status.code).left)
      else
        for {
          text <- resp.body.through(fs2.text.utf8Decode).compile.foldMonoid
          res = JsParser(text)
            .flatMap(_.as[A])
            .leftMap(JsonClient.DecodingError(_))
        } yield res
    }
  }
~~~~~~~~

The `.through(fs2.text.utf8Decode)` is to convert a `Stream[Task, Byte]` into a
`Stream[Task, String]`, with `.compile.foldMonoid` interpreting it with our
`Task` and combining all the parts using the `Monoid[String]`, giving us a
`Task[String]`.

We then parse the string as JSON and use the `JsDecoder[A]` to create the
required output.

This is our implementation of `.get`

{lang="text"}
~~~~~~~~
  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[A] =
    I.liftIO(
        H.fetch(
          http4s.Request[Task](
            uri = convert(uri),
            headers = convert(headers)
          )
        )(handler[A])
      )
      .emap(identity)
~~~~~~~~

`.get` is all plumbing: we convert our input types into the `http4s.Request`,
then call `.fetch` on the `Client` with our `handler`. This gives us back a
`Task[Error \/ A]`, but we need to return a `F[A]`. Therefore we use the
`MonadIO.liftIO` to create a `F[Error \/ A]` and then `.emap` to push the error
into the `F`.

Unfortunately, if we try to compile this code it will fail. The error will look
something like

{lang="text"}
~~~~~~~~
  [error] BlazeJsonClient.scala:95:64: could not find implicit value for parameter
  [error]  F: cats.effect.Sync[scalaz.ioeffect.Task]
~~~~~~~~

Basically, something about a missing cat.

The reason for this failure is that http4s is using a different core FP library,
not Scalaz. Thankfully, `scalaz-ioeffect` provides a compatibility layer and the
[shims](https://github.com/djspiewak/shims) project provides seamless (until it isn't) implicit conversions. We can
get our code to compile with these dependencies:

{lang="text"}
~~~~~~~~
  libraryDependencies ++= Seq(
    "com.codecommit" %% "shims"                % "1.4.0",
    "org.scalaz"     %% "scalaz-ioeffect-cats" % "2.10.1"
  )
~~~~~~~~

and these imports

{lang="text"}
~~~~~~~~
  import shims._
  import scalaz.ioeffect.catz._
~~~~~~~~

The implementation of `.post` is similar but we must also provide an instance of

{lang="text"}
~~~~~~~~
  EntityEncoder[Task, String Refined UrlEncoded]
~~~~~~~~

Thankfully, the `EntityEncoder` typeclass provides conveniences to let us derive
one from the existing `String` encoder

{lang="text"}
~~~~~~~~
  implicit val encoder: EntityEncoder[Task, String Refined UrlEncoded] =
    EntityEncoder[Task, String]
      .contramap[String Refined UrlEncoded](_.value)
      .withContentType(
        `Content-Type`(MediaType.`application/x-www-form-urlencoded`)
      )
~~~~~~~~

The only difference between `.get` and `.post` is the way we construct our `http4s.Request`

{lang="text"}
~~~~~~~~
  http4s.Request[Task](
    method = http4s.Method.POST,
    uri = convert(uri),
    headers = convert(headers)
  )
  .withBody(payload.toUrlEncoded)
~~~~~~~~

and the final piece is the constructor, which is a case of calling `Http1Client`
with a configuration object

{lang="text"}
~~~~~~~~
  object BlazeJsonClient {
    def apply[F[_]](
      implicit
      F: MonadError[F, JsonClient.Error],
      I: MonadIO[F, Throwable]
    ): Task[JsonClient[F]] =
      Http1Client(BlazeClientConfig.defaultConfig).map(new BlazeJsonClient(_))
  }
~~~~~~~~


### `BlazeUserInteraction`

We need to spin up an HTTP server, which is a lot easier than it sounds. First,
the imports

{lang="text"}
~~~~~~~~
  import org.http4s._
  import org.http4s.dsl._
  import org.http4s.server.Server
  import org.http4s.server.blaze._
~~~~~~~~

We need to create a `dsl` for our effect type, which we then import

{lang="text"}
~~~~~~~~
  private val dsl = new Http4sDsl[Task] {}
  import dsl._
~~~~~~~~

Now we can use the [http4s dsl](https://http4s.org/v0.18/dsl/) to create HTTP endpoints. Rather than describe
everything that can be done, we will simply implement the endpoint which is
similar to any of other HTTP DSLs

{lang="text"}
~~~~~~~~
  private object Code extends QueryParamDecoderMatcher[String]("code")
  private val service: HttpService[Task] = HttpService[Task] {
    case GET -> Root :? Code(code) => ...
  }
~~~~~~~~

The return type of each pattern match is a `Task[Response[Task]]`. In our
implementation we want to take the `code` and put it into the `ptoken` promise:

{lang="text"}
~~~~~~~~
  final class BlazeUserInteraction private (
    pserver: Promise[Throwable, Server[Task]],
    ptoken: Promise[Throwable, String]
  ) extends UserInteraction[Task] {
    ...
    private val service: HttpService[Task] = HttpService[Task] {
      case GET -> Root :? Code(code) =>
        ptoken.complete(code) >> Ok(
          "That seems to have worked, go back to the console."
        )
    }
    ...
  }
~~~~~~~~

but the definition of our services routes is not enough, we need to launch a
server, which we do with `BlazeBuilder`

{lang="text"}
~~~~~~~~
  private val launch: Task[Server[Task]] =
    BlazeBuilder[Task].bindHttp(0, "localhost").mountService(service, "/").start
~~~~~~~~

Binding to port `0` makes the operating system assign an ephemeral port. We can
discover which port it is actually running on by querying the `server.address`
field.

Our implementation of the `.start` and `.stop` methods is now straightforward

{lang="text"}
~~~~~~~~
  def start: Task[String Refined Url] =
    for {
      server  <- launch
      updated <- pserver.complete(server)
      _ <- if (updated) Task.unit
           else server.shutdown *> fail("server was already running")
    } yield mkUrl(server)
  
  def stop: Task[CodeToken] =
    for {
      server <- pserver.get
      token  <- ptoken.get
      _      <- IO.sleep(1.second) *> server.shutdown
    } yield CodeToken(token, mkUrl(server))
  
  private def mkUrl(s: Server[Task]): String Refined Url = {
    val port = s.address.getPort
    Refined.unsafeApply(s"http://localhost:${port}/")
  }
  private def fail[A](s: String): String =
    Task.fail(new IOException(s) with NoStackTrace)
~~~~~~~~

The `1.second` sleep is necessary to avoid shutting down the server before the
response is sent back to the browser. IO doesn't mess around when it comes to
concurrency performance!

Finally, to create a `BlazeUserInteraction`, we just need the two uninitialised
promises

{lang="text"}
~~~~~~~~
  object BlazeUserInteraction {
    def apply(): Task[BlazeUserInteraction] = {
      for {
        p1 <- Promise.make[Void, Server[Task]].widenError[Throwable]
        p2 <- Promise.make[Void, String].widenError[Throwable]
      } yield new BlazeUserInteraction(p1, p2)
    }
  }
~~~~~~~~

We could use `IO[Void, ?]` instead, but since the rest of our application is
using `Task` (i.e. `IO[Throwable, ?]`), we `.widenError` to avoid introducing
any boilerplate that would distract us.


## Thank You

And that is it! Congratulations on reaching the end.

If you learnt something from this book, then please tell your friends. This book
does not have a marketing department, so word of mouth is the only way that
readers find out about it.

Get involved with Scalaz by joining the [gitter chat room](https://gitter.im/scalaz/scalaz). From there you can ask
for advice, help newcomers (you're an expert now), and contribute to the next
release.


