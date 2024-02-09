interface Titanic {
  name: string,
  survived: boolean
}

let tn : Titanic[] = [
  { name:"Joe", survived:false },
  { name:"Jane", survived:true },
]

// "drops a column from a data frame"
// (Omit is defined in the TypeScript library - this can be expanded to something)
function drop<T, K extends string>(data:T[], key:K) : Omit<T, K>[] {
  throw "not implemented"
}

drop(tn, "survived")[0].name
drop(tn, "survived")[0].survived // ERROR

// Inlined the definition of Omit (but not sure how Exclude works!)
function drop2<T, K extends string>(data:T[], key:K) : { [P in Exclude<keyof T, K>] : T[P] }[] {
  throw "not implemented"
}

drop2(tn, "survived")[0].name
drop2(tn, "survived")[0].survived // ERROR


// "rename column"
// (can this be extended to take a record of renamings? no idea...)
function rename1<T, K1 extends string, K2 extends string>
    (data:T[], k1:K1, k2:K2) : 
      { [P in keyof T as (P extends K1 ? K2 : P)]: T[P] }[] {
  throw "not implemented"
}

rename1(tn, "survived", "lived")[0].name
rename1(tn, "survived", "lived")[0].lived
rename1(tn, "survived", "lived")[0].survived // ERROR
