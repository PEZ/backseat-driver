What's the function of the temperature parameter? WHat's the min/max?
* Kreativitetsnivå. Min 0, Max 1, default 0,5 <-- hallucination
* 0 -> 2, default 1

0 => Alltid samma svar, för kod kan det vara bra
(Kan också ses som förutsägbarhet)

När borde man använda Assistant API i stället för Completions?

json mode

Hur hanterar man att det tar väldigt lång tid att få svar ibland?

Jag lyckas inte få Assistant att tillförlitligt fatta att jag skickar kontext
via instruktionerna. Är det fel sätt att göra det?

När jag skickar contextet via user message hittar botten det alltid, men den blir också mycket, mycket dyrare i API-kostnade. Kanske 100x, kanske mer...
* Eller, det kan ha varit att jag skickade något binärt skräp, API:et slutade fungera i någon timme.

Hur funkar code-interpreter?


Knasiga svar ibland:

```
Me: Hellio
....

Backseat Driver:
It looks like you're trying to enter some input. Is there anything specific you would like assistance with?
```