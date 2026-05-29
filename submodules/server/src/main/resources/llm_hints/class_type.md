## Class

In CRML, a **class** is a domain of all objects sharing the same set of named attributes. Unlike primitive types, a class groups multiple attributes (which may themselves be of any CRML type, including other classes) under a single named entity. Instances of a class are called **objects** and are created with the `new` keyword. Classes support **inheritance** via `extends`, and **multiple inheritance**.

### Syntax forms

| Form | Syntax |
|---|---|
| Basic class definition | `class C is { Type a1; Type a2; ... };` |
| Attribute with fixed value | `Type a is expr;` |
| External attribute (value supplied by binding) | `Type a is external;` |
| Class extending one parent | `class C2 is { ... } extends C1;` |
| Class extending multiple parents | `class C is { ... } extends { C1, C2, ..., Cn };` |
| Object instantiation | `C obj is new C (attr1 = value1, ...);` |

### Key concepts

- **`extends`** — a subclass inherits all attributes of its parent class(es). Multiple parent classes can be listed in braces.
- **`external`** — an attribute whose value is not defined inside the class and will be supplied by a Modelica model.
- **Object (`new`)** — creates an instance of a class.

### Examples

#### Partial base class and extension

```crml
model ClassBatterySystem is {
    // Abstract base class — cannot be instantiated directly
    class Equipment is {
        String id;
        Boolean inOperation is external;
    };

    // BatteryCell extends Equipment, adding its own external attributes
    class BatteryCell is {
        Real voltage is external;
        Real temperature is external;
        Boolean overTemp is external;
        // Derived attribute: requirement expressed as a formula
        Boolean safeTemp is 'during' inOperation 'ensure' not overTemp;
    } extends Equipment;

    // BatteryPack groups three cells, itself extending Equipment
    class BatteryPack is {
        BatteryCell C1;
        BatteryCell C2;
        BatteryCell C3;
    } extends Equipment;
};
```

#### Object instantiation

```crml
model ClassInstantiation is {
    class Equipment is {
        String id;
        Boolean inOperation is external;
    };

    class BatteryCell is {
        Real voltage is external;
        Real temperature is external;
        Boolean overTemp is external;
        Boolean safeTemp is 'during' inOperation 'ensure' not overTemp;
    } extends Equipment;

    // Instantiate a BatteryCell object, binding the 'id' external attribute
    BatteryCell cell1 is new BatteryCell (id = "Cell1");
};
```
