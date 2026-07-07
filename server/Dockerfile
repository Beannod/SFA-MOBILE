FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY . .

# find first csproj, restore + publish to fixed dll name
RUN PROJECT=$(find . -maxdepth 3 -name "*.csproj" | head -n 1) && \
    dotnet restore "$PROJECT" && \
    dotnet publish "$PROJECT" -c Release -o /app/out /p:AssemblyName=app

FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /app
COPY --from=build /app/out .
ENV ASPNETCORE_URLS=http://0.0.0.0:10000
EXPOSE 10000
ENTRYPOINT ["dotnet", "app.dll"]
