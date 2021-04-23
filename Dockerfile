FROM clojure:tools-deps

# Create the bot user
RUN useradd -ms /bin/bash bot
USER bot
RUN mkdir /home/bot/app
WORKDIR /home/bot/app

# Build a jar and execute that
COPY --chown=bot . .
RUN clojure -Xuberjar
CMD java -jar target/bot.jar
