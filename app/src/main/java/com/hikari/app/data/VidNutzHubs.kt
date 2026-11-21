package com.hikari.app.data

object VidNutzHubs {

    fun sub(name: String, count: Int = 0): VidNutzSub {
        val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return VidNutzSub(id = id, name = name, count = count, queries = listOf(name))
    }

    val all: List<VidNutzHub> = listOf(
        VidNutzHub("always_on", "24/7 & Live", listOf(
            sub("24/7 Live Cams & Safari"), sub("24/7 News Live"),
            sub("Music 24/7 & Long Mixes"), sub("Fireplace, Rain, Nature & Sleep Loops"),
        )),
        VidNutzHub("around_the_world", "Around the World", listOf(
            sub("India"), sub("Turkiye"), sub("Latin America"), sub("Africa"),
        )),
        VidNutzHub("big_talk", "Big Talk", listOf(
            sub("Long Interviews"), sub("Science & Health"), sub("History & War Talks"),
            sub("Sport & Fight Talk"), sub("Business & Money"),
        )),
        VidNutzHub("cars_motoring", "Cars & Motoring", listOf(
            sub("Barn Finds"), sub("Junkyard Revivals"), sub("Off-Road Rescue"),
            sub("Full Restorations"), sub("Project Builds"), sub("Road Trip Specials"),
        )),
        VidNutzHub("comedy", "Comedy", listOf(
            sub("Funny Home Videos"), sub("Pranks"), sub("Stand-up"),
            sub("Comedy Central"), sub("Fails"), sub("Stunts & Big Builds"),
        )),
        VidNutzHub("cooking_food", "Cooking & Food", listOf(
            sub("Cooking Competition"), sub("Cook Along & Food Science"),
            sub("Full Cooking Shows"), sub("Kitchens of the World"), sub("BBQ & Baking"),
        )),
        VidNutzHub("crime", "Crime", listOf(
            sub("COPS"), sub("True Crime"), sub("Police"), sub("Courtroom"),
            sub("Murder Cases"), sub("Cold Cases"), sub("Prisons"),
        )),
        VidNutzHub("documentaries", "Documentaries", listOf(
            sub("Nature"), sub("History"), sub("Science"), sub("Space"),
            sub("Engineering"), sub("War & Military"), sub("Travel"), sub("Wildlife"),
        )),
        VidNutzHub("gaming", "Gaming", listOf(
            sub("Minecraft"), sub("Roblox & Family Gaming"), sub("Fortnite & Battle Royale"),
            sub("Retro, Speedrunning & Game Docs"), sub("Official Esports & Directs"),
        )),
        VidNutzHub("health_and_wellbeing", "Health & Wellbeing", listOf(
            sub("Walking & Workouts"), sub("Stretch, Recover & Relax"),
        )),
        VidNutzHub("history", "History", listOf(
            sub("Full-Length Documentaries"), sub("Living History"),
            sub("Ancient & Medieval"), sub("War & Military History"),
        )),
        VidNutzHub("influencers", "Influencers", listOf(
            sub("MrBeast Universe"), sub("The MrBeast Crew"), sub("Big-Budget Challenges"),
            sub("Family Challenge Squad"), sub("Big Family Channels"),
        )),
        VidNutzHub("kids", "Kids", listOf(
            sub("Preschool Favourites"), sub("Bedtime & Calm"), sub("Songs & Rhymes"),
            sub("Numbers, Letters & Colours"), sub("Cartoon Network & Nickelodeon"),
            sub("Disney & Magical Adventures"),
        )),
        VidNutzHub("live_cams", "Live Cams", listOf(
            sub("Wildlife"), sub("Birds"), sub("Nests"), sub("Bears"),
            sub("Dog Parks"), sub("Beaches"), sub("Railways"), sub("Volcanoes"),
        )),
        VidNutzHub("makers_and_builders", "Makers & Builders", listOf(
            sub("Workshop & Making"), sub("Restoration & Forging"),
            sub("Woodwork, 3D Printing & Models"), sub("Home Renovation"),
        )),
        VidNutzHub("music_concerts", "Music & Concerts", listOf(
            sub("Full Concerts"), sub("Festival Headline Sets"), sub("DJ Sets in Amazing Places"),
            sub("Tiny Desk"), sub("Live in the Studio"), sub("Classic Rock Live"),
        )),
        VidNutzHub("nature_wild", "Nature & Wild", listOf(
            sub("Big Cats"), sub("Oceans & Sharks"), sub("Africa"),
            sub("Wild Rescues"), sub("4K Nature Ambience"), sub("Survival in the Wild"),
        )),
        VidNutzHub("real_life_tv", "Real Life TV", listOf(
            sub("MasterChef World"), sub("Property & Renovation"), sub("Airports & Borders"),
            sub("Extreme Jobs"), sub("Life at Sea"), sub("Daytime Court"),
        )),
        VidNutzHub("science_and_spectacle", "Science & Spectacle", listOf(
            sub("Stunts & Challenges"), sub("Science & Curiosity"),
        )),
        VidNutzHub("tech_and_gear", "Tech & Gear", listOf(
            sub("Tech Reviews"),
        )),
        VidNutzHub("ufo", "UFO & Space", listOf(
            sub("UFO / UAP"), sub("Space Mysteries"), sub("NASA"), sub("Astronomy"),
        )),
        VidNutzHub("walking_tours", "Walking Tours", listOf(
            sub("4K City Walks"), sub("Night Walks"), sub("Beaches & Coastlines"),
            sub("Japan & Asia"), sub("Europe on Foot"), sub("Scenic Drives"),
        )),
    )

    val trending = VidNutzHub("trending", "Trending", listOf(
        sub("Trending Now"), sub("Popular This Week"),
    ))
    val liveStreams = VidNutzHub("live_streams", "Live Streams", listOf(
        sub("Live Streams Now"), sub("24/7 Live TV"),
    ))

    val menu: List<VidNutzHub> = listOf(trending, liveStreams) + all

    fun byId(id: String): VidNutzHub? = menu.firstOrNull { it.id == id }
}
