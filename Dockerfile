## Multi-stage Dockerfile for building and running SFA API with frontend
## - Installs msodbcsql18 driver needed by production DB connectivity
## - Copies `frontend/web-ui` into `backend/server/wwwroot`
## - Publishes the ASP.NET Core app and produces a small runtime image

FROM mcr.microsoft.com/dotnet/sdk:7.0 AS build

ENV DEBIAN_FRONTEND=noninteractive

# Install prerequisites for msodbcsql18
RUN apt-get update \
 && apt-get install -y curl gnupg2 apt-transport-https ca-certificates unixodbc-dev software-properties-common \
 && curl https://packages.microsoft.com/keys/microsoft.asc | apt-key add - \
 && curl https://packages.microsoft.com/config/ubuntu/22.04/prod.list > /etc/apt/sources.list.d/mssql-release.list \
 && apt-get update \
 && ACCEPT_EULA=Y apt-get install -y msodbcsql18 \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /src

# Copy everything (use rebuild caching smartly)
COPY . ./

# Ensure frontend static files are present in backend/wwwroot
RUN if [ -d frontend/web-ui ]; then \
      mkdir -p backend/server/wwwroot && cp -R frontend/web-ui/* backend/server/wwwroot/ ; \
    fi

# Restore and publish the API
RUN dotnet restore backend/server/SfaApi.csproj
RUN dotnet publish backend/server/SfaApi.csproj -c Release -o /app/publish

FROM mcr.microsoft.com/dotnet/aspnet:7.0 AS runtime
WORKDIR /app
COPY --from=build /app/publish ./

EXPOSE 5000
ENV ASPNETCORE_URLS=http://+:5000
ENTRYPOINT ["dotnet", "SfaApi.dll"]
