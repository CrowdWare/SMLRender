Page {
    id: "innernetWelcome"
    background: "animated:InnerNetFlow"
    title: "InnerNet - Welcome"
    
  Column {
    alignment: "center"
    padding: 24

    Card {
      maxWidth: 720
      padding: 24
      borderRadius: 16
      backgroundColor: "#FF0A0A14"
      elevation: 8

      Column {
        alignment: "center"

        Text {
          text: "InnerNet"
          fontSize: 30
          fontWeight: "bold"
          padding: "0,0,0,4"
        }

        Text {
          text: "Free. Uncensored. Human."
          fontSize: 16
          opacity: 85
          padding: "0,0,0,24"
        }

        Button {
          text: "Join now"
          color: "primary"
          onClick: "openRegistration()"
          padding: "0,0,0,24"
        }

        // Accordion 1 – What is the InnerNet?
        Accordion {
          title: "What is the InnerNet?"
          initiallyOpen: true
          content:
            Text {
              text: "
The InnerNet is a decentralized social network without ads,
without tracking and without central control. Your identity
and your data stay in your hands – not on someone else’s server.
"
            }
        }

        // Accordion 2 – Circle of Trust
        Accordion {
          title: "How does the Circle of Trust work?"
          content:
            Text {
              text: "
Your Circle of Trust is made of your direct contacts (FRIEND)
and their contacts (FOAF – friends of friends). You decide
whether something is visible only to your Circle, private
to selected people or public to the whole network.
"
            }
        }

        // Accordion 3 – Self pinning
        Accordion {
          title: "Why do I have to pin my own data?"
          content:
            Column {
              Text {
                text: "
The InnerNet is truly decentralized. There is no central
server which owns your content.

Instead, your public profile, your public posts and your
user index are stored on IPFS and pinned by you (or a hub
you trust).

Pinning means: you decide what stays online and nobody can
silently delete your content behind your back.
"
              }
              Spacer { height: 8 }
              Text {
                text: "Example: using a pinning service like Pinata."
                fontSize: 13
                opacity: 80
              }
              Link {
                text: "Learn more about IPFS pinning (Pinata)"
                link: "web:https://pinata.cloud"
                padding: "0,4,0,0"
              }
            }
        }

        // Accordion 4 – Funding
        Accordion {
          title: "How is the InnerNet funded?"
          content:
            Text {
              text: "
There are no ads, no data selling and no paywalls.

The InnerNet is kept alive by people who run voluntary hubs
(nodes) and share storage and bandwidth.

You can use the InnerNet for free. If you want, you can
support a hub or contribute resources yourself.
"
            }
        }

        // Accordion 5 – Support the team
        Accordion {
          title: "Support the InnerNet team"
          content:
            Column {
              Text {
                text: "
If you like the vision and want to say thank you,
you can buy the core team a coffee. Completely optional.
"
              }
              Link {
                text: "☕ Buy us a coffee (BuyMeACoffee)"
                link: "web:https://www.buymeacoffee.com/"
                padding: "0,8,0,0"
              }
            }
        }

      }
    }
  }
}